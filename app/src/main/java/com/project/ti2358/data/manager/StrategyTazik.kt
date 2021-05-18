package com.project.ti2358.data.manager

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.project.ti2358.R
import com.project.ti2358.TheApplication
import com.project.ti2358.data.model.dto.Candle
import com.project.ti2358.data.model.dto.Currency
import com.project.ti2358.service.*
import kotlinx.coroutines.*
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.lang.Exception
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

@KoinApiExtension
class StrategyTazik : KoinComponent {
    private val stockManager: StockManager by inject()
    private val depositManager: DepositManager by inject()
    private val strategySpeaker: StrategySpeaker by inject()
    private val strategyTelegram: StrategyTelegram by inject()
    private val strategyBlacklist: StrategyBlacklist by inject()

    var stocks: MutableList<Stock> = mutableListOf()
    var stocksSelected: MutableList<Stock> = mutableListOf()

    var stocksToPurchase: MutableList<PurchaseStock> = mutableListOf()
    var stocksToPurchaseClone: MutableList<PurchaseStock> = mutableListOf()
    var stocksTickerInProcess: MutableMap<String, Job> = ConcurrentHashMap()

    var basicPercentLimitPriceChange: Double = 0.0
    var started: Boolean = false

    var scheduledStartTime: Calendar? = null
    var currentSort: Sorting = Sorting.DESCENDING

    companion object {
        const val PercentLimitChangeDelta = 0.05
    }

    fun process(numberSet: Int): MutableList<Stock> {
        val all = stockManager.getWhiteStocks()
        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        stocks = all.filter { (it.getPriceNow() > min && it.getPriceNow() < max) || it.getPriceNow() == 0.0 }.toMutableList()
        stocks.sortBy { it.changePrice2300DayPercent }
        loadSelectedStocks(numberSet)
        return stocks
    }

    private fun loadSelectedStocks(numberSet: Int) {
        stocksSelected.clear()

        val setList: List<String> = if (numberSet == 1) SettingsManager.getTazikSet1() else SettingsManager.getTazikSet2()
        stocksSelected = stocks.filter { it.ticker in setList }.toMutableList()
    }

    private fun saveSelectedStocks(numberSet: Int) {
        val setList = stocksSelected.map { it.ticker }.toList()
        val preferences = PreferenceManager.getDefaultSharedPreferences(TheApplication.application.applicationContext)
        val editor: SharedPreferences.Editor = preferences.edit()
        val key = if (numberSet == 1) TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_set_1) else TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_set_2)
        editor.putString(key, setList.joinToString(separator = " "))
        editor.apply()
    }

    fun resort(): MutableList<Stock> {
        currentSort = if (currentSort == Sorting.DESCENDING) Sorting.ASCENDING else Sorting.DESCENDING
        stocks.sortBy {
            val sign = if (currentSort == Sorting.ASCENDING) 1 else -1
            val multiplier = if (it in stocksSelected) 100 else 1
            it.changePrice2300DayPercent * sign - multiplier
        }
        return stocks
    }

    fun setSelected(stock: Stock, value: Boolean, numberSet: Int) {
        if (value) {
            if (stock !in stocksSelected)
                stocksSelected.add(stock)
        } else {
            stocksSelected.remove(stock)
        }
        stocksSelected.sortBy { it.changePrice2300DayPercent }

        saveSelectedStocks(numberSet)
    }

    fun isSelected(stock: Stock): Boolean {
        return stock in stocksSelected
    }

    fun getPurchaseStock(): MutableList<PurchaseStock> {
        stocksToPurchase.clear()

        val percent = SettingsManager.getTazikChangePercent()
        val totalMoney: Double = SettingsManager.getTazikPurchaseVolume().toDouble()
        var onePiece: Double = totalMoney / SettingsManager.getTazikPurchaseParts()
        val before11 = Utils.isSessionBefore11()

        stocksToPurchase = stocksSelected.map {
            PurchaseStock(it).apply {
                percentLimitPriceChange = -abs(percent)

                // отнять процент роста с начала премаркета, если мы запускаем в 10
                if (before11) {
                    val deltaPercent = it.getPriceNow() / it.getPrice0145() * 100.0 - 100.0
                    if (!deltaPercent.isNaN() && deltaPercent > 0.0) {
                        percentLimitPriceChange -= abs(deltaPercent * 0.5)
                    }
                }

                if (stock.instrument.currency == Currency.RUB) {
                    onePiece *= Utils.getUSDRUB()
                }
                lots = (onePiece / (stock.getPriceNow() * stock.instrument.lot)).roundToInt()
                updateAbsolutePrice()
                status = PurchaseStatus.WAITING
            }
        }.toMutableList()

        // удалить все бумаги, у которых 0 лотов = не хватает на покупку одной части
        stocksToPurchase.removeAll { it.lots == 0 }

        // удалить все бумаги, у которых недавно или скоро отчёты
        if (SettingsManager.getTazikExcludeReports()) {
            stocksToPurchase.removeAll { it.stock.report != null }
        }

        // удалить все бумаги, у которых скоро дивы
        if (SettingsManager.getTazikExcludeDivs()) {
            stocksToPurchase.removeAll { it.stock.dividend != null }
        }

        // удалить все бумаги, которые уже есть в портфеле, чтобы избежать коллизий
        if (SettingsManager.getTazikExcludeDepo()) {
            stocksToPurchase.removeAll { p -> depositManager.portfolioPositions.any { it.ticker == p.ticker } }
        }

        // удалить все бумаги из чёрного списка
        val blacklist = strategyBlacklist.getBlacklistStocks()
        stocksToPurchase.removeAll { it.ticker in blacklist.map { stock -> stock.ticker } }

        stocksToPurchaseClone = stocksToPurchase.toMutableList()

        return stocksToPurchase
    }

    fun getNotificationTitle(): String {
        if (started) return "Работает автотазик!"

        if (scheduledStartTime == null) {
            return "Старт тазика через ???"
        } else {
            val now = Calendar.getInstance(TimeZone.getDefault())
            val current = scheduledStartTime?.timeInMillis ?: 0
            val scheduleDelay = current - now.timeInMillis

            val allSeconds = scheduleDelay / 1000
            val hours = allSeconds / 3600
            val minutes = (allSeconds - hours * 3600) / 60
            val seconds = allSeconds % 60

            fixPrice()
            if (hours + minutes + seconds <= 0) {
                startStrategy(true)
            }

            return "Старт тазика через %02d:%02d:%02d".format(hours, minutes, seconds)
        }
    }

    @Synchronized
    fun getTotalPurchaseString(): String {
        val volume = SettingsManager.getTazikPurchaseVolume().toDouble()
        val p = SettingsManager.getTazikPurchaseParts()
        val volumeShares = SettingsManager.getTazikMinVolume()
        return String.format("%d из %d по %.2f$, просадка %.2f / %.2f / %.2f / %d",
            stocksTickerInProcess.size, p, volume / p, basicPercentLimitPriceChange, SettingsManager.getTazikTakeProfit(), SettingsManager.getTazikApproximationFactor(), volumeShares)
    }

    fun getNotificationTextShort(): String {
        val price = getTotalPurchaseString()
        var tickers = ""
        for (stock in stocksToPurchase) {
            tickers += "${stock.ticker} "
        }
        return "$price:\n$tickers"
    }

    fun getNotificationTextLong(): String {
        val volume = 0
        stocksToPurchase.sortBy { abs(it.stock.getPriceNow(volume) / it.tazikPrice * 100 - 100) }
        stocksToPurchase.sortBy { it.stock.getPriceNow(volume) / it.tazikPrice * 100 - 100 }
        stocksToPurchase.sortBy { it.status }

        var tickers = ""
        for (stock in stocksToPurchase) {
            val change = (100 * stock.stock.getPriceNow(volume)) / stock.tazikPrice - 100
            if (change >= -0.01 && stock.status == PurchaseStatus.WAITING && stocksToPurchase.size > 5) continue

            var vol = 0
            if (stock.stock.minuteCandles.isNotEmpty()) {
                vol = stock.stock.minuteCandles.last().volume
            }
            tickers += "${stock.ticker} ${stock.percentLimitPriceChange.toPercent()} = " +
                    "${stock.tazikPrice.toMoney(stock.stock)} ➡ ${stock.stock.getPriceNow(volume).toMoney(stock.stock)} = " +
                    "${change.toPercent()} ${stock.getStatusString()} v=${vol}\n"
        }
        if (tickers == "") tickers = "только отрицательные бумаги ⏳"

        return tickers
    }

    fun prepareStrategy(scheduled : Boolean, time: String) {
        basicPercentLimitPriceChange = SettingsManager.getTazikChangePercent()

        if (!scheduled) {
            startStrategy(scheduled)
            return
        }

        started = false

        val differenceHours: Int = Utils.getTimeDiffBetweenMSK()
        val dayTime = time.split(":").toTypedArray()
        if (dayTime.size < 3) {
            Utils.showToastAlert("Неверный формат времени $time")
            return
        }

        val hours = Integer.parseInt(dayTime[0])
        val minutes = Integer.parseInt(dayTime[1])
        val seconds = Integer.parseInt(dayTime[2])

        scheduledStartTime = Calendar.getInstance(TimeZone.getDefault())
        scheduledStartTime?.let {
            it.add(Calendar.HOUR_OF_DAY, -differenceHours)
            it.set(Calendar.HOUR_OF_DAY, hours)
            it.set(Calendar.MINUTE, minutes)
            it.set(Calendar.SECOND, seconds)
            it.add(Calendar.HOUR_OF_DAY, differenceHours)

            val now = Calendar.getInstance(TimeZone.getDefault())
            val scheduleDelay = it.timeInMillis - now.timeInMillis
            if (scheduleDelay < 0) {
                Utils.showToastAlert("Ошибка! Отрицательное время!? втф = $scheduleDelay")
            }
        }
    }

    private fun fixPrice() {
        if (started) return

        // зафикировать цену, чтобы change считать от неё
        for (purchase in stocksToPurchase) {
            purchase.tazikPrice = purchase.stock.getPriceNow()
        }
    }

    @Synchronized
    private fun startStrategy(scheduled: Boolean) {
        if (scheduled) {
            GlobalScope.launch(Dispatchers.Main) {
                stockManager.reloadClosePrices()

                // костыль!
                started = false
                fixPrice()
                started = true
            }
        } else {
            fixPrice()
        }
        started = true
        stocksTickerInProcess.forEach {
            try {
                if (it.value.isActive) {
                    it.value.cancel()
                }
            } catch (e: Exception) {

            }
        }
        stocksTickerInProcess.clear()
        strategyTelegram.sendTazikStart(true)
    }

    @Synchronized
    fun stopStrategy() {
        started = false
        stocksTickerInProcess.forEach {
            try {
                if (it.value.isActive) {
                    it.value.cancel()
                }
            } catch (e: Exception) {

            }
        }
        stocksTickerInProcess.clear()
        strategyTelegram.sendTazikStart(false)
    }

    fun addBasicPercentLimitPriceChange(sign: Int) {
        basicPercentLimitPriceChange += sign * PercentLimitChangeDelta

        for (purchase in stocksToPurchase) {
            purchase.percentLimitPriceChange += sign * PercentLimitChangeDelta
            if (purchase.stock.minuteCandles.isNotEmpty()) {
                processStrategy(purchase.stock, purchase.stock.minuteCandles.last())
            }
        }
    }

    @Synchronized
    private fun isAllowToBuy(purchase: PurchaseStock, change: Double, volume: Int): Boolean {
        if (purchase.tazikPrice == 0.0 ||                   // стартовая цена нулевая = не загрузились цены
            abs(change) > 50 ||                             // конечная цена нулевая или просто огромная просадка
            change > 0 ||                                   // изменение положительное
            change > purchase.percentLimitPriceChange ||    // изменение не в пределах наших настроек
            volume < SettingsManager.getTazikMinVolume()    // если объём свечи меньше настроек
        ) {
            return false
        }

        // если тазик утренний, то проверять, чтоб цена покупки была ниже цены закрытия
        val buyPrice = purchase.tazikPrice - purchase.tazikPrice / 100.0 * abs(purchase.percentLimitPriceChange)
        if (Utils.isSessionBefore11() && buyPrice > purchase.stock.getPrice0145()) {
            return false
        }

        val ticker = purchase.ticker

        // лимит на заявки исчерпан?
        if (stocksTickerInProcess.size >= SettingsManager.getTazikPurchaseParts()) return false

        // проверить, если бумага в депо и усреднение отключено, то запретить тарить
        if (depositManager.portfolioPositions.find { it.ticker == purchase.ticker } != null && !SettingsManager.getTazikAllowAveraging()) {
            return false
        }

        // ещё не брали бумагу?
        if (ticker !in stocksTickerInProcess) {
            return true
        }

        // разрешить усреднение?
        if (SettingsManager.getTazikAllowAveraging()) {
            return true
        }

        return false
    }

    fun buyFirstOne() {
        for (purchase in stocksToPurchase) {
            val closingPrice = purchase.stock.candleToday?.closingPrice ?: 0.0
            if (closingPrice == 0.0 || purchase.stock.ticker in stocksTickerInProcess) continue

            purchase.stock.candleToday?.let {
                processBuy(purchase, purchase.stock, it)
            }
            break
        }
    }

    @Synchronized
    fun processUpdate() {
        if (!started) return

        // если стратегия стартанула и какие-то корутины уже завершились, то убрать их, чтобы появился доступ для новых покупок
        for (value in stocksTickerInProcess) {
            if (!value.value.isActive) {
                val key = value.key
                stocksTickerInProcess.remove(key)
            }
        }
    }

    fun processStrategy(stock: Stock, candle: Candle) {
        if (!started) return

        val ticker = stock.ticker

        // если бумага не в списке скана - игнорируем
        synchronized(stocksToPurchaseClone) {
            val sorted = stocksToPurchaseClone.find { it.ticker == ticker }
            sorted?.let { purchase ->
                val change = candle.closingPrice / purchase.tazikPrice * 100.0 - 100.0
                val volume = candle.volume

                if (isAllowToBuy(purchase, change, volume)) {
                    processBuy(purchase, stock, candle)
                }
            }
        }
    }

    @Synchronized
    private fun processBuy(purchase: PurchaseStock, stock: Stock, candle: Candle) {
        // завершение стратегии
        val parts = SettingsManager.getTazikPurchaseParts()
        if (stocksTickerInProcess.size >= parts) {
            stopStrategy()
            return
        }

        val change = candle.closingPrice / purchase.tazikPrice * 100.0 - 100.0

        val baseProfit = SettingsManager.getTazikTakeProfit()
        val totalMoney: Double = SettingsManager.getTazikPurchaseVolume().toDouble()
        val onePiece: Double = totalMoney / SettingsManager.getTazikPurchaseParts()
        purchase.lots = (onePiece / stock.getPriceNow()).roundToInt()

        // ищем цену максимально близкую к просадке
        var delta = abs(change) - abs(purchase.percentLimitPriceChange)     // 3.0% - 1.0% = 2.0%

        // коэф приближения к нижней точке, в самом низу могут не налить
        val factor = abs(SettingsManager.getTazikApproximationFactor())     // 0.25%
        delta *= factor                                                     // 2.0% * 0.25% = 0.5%

        // корректируем % падения для покупки
        val percent = abs(purchase.percentLimitPriceChange) + delta         // 1.0% + 0.5% = 1.5%

        // вычислияем финальную цену лимитки
        val buyPrice = purchase.tazikPrice - purchase.tazikPrice / 100.0 * abs(percent)     // 100$ - 100$ * 0.015% = 98.5$

        // вычисляем процент профита после сдвига лимитки ниже

        // финальный профит
        delta *= factor                                                     // 0.5% * 0.25% = 0.125%
        var finalProfit = baseProfit + abs(delta)                           // 0.9% + 0.125% = 1.025%

        if (baseProfit == 0.0) finalProfit = 0.0
        val job = purchase.buyLimitFromBid(buyPrice, finalProfit, 1, SettingsManager.getTazikOrderLifeTimeSeconds())
        if (job != null) {
            stocksTickerInProcess[stock.ticker] = job
        }

        var sellPrice = buyPrice + buyPrice / 100.0 * finalProfit
        sellPrice = Utils.makeNicePrice(sellPrice)

        strategySpeaker.speakTazik(purchase, change)
        strategyTelegram.sendTazikBuy(purchase, buyPrice, sellPrice, purchase.tazikPrice, candle.closingPrice, change, stocksTickerInProcess.size, parts)
        purchase.tazikPrice = candle.closingPrice
    }
}