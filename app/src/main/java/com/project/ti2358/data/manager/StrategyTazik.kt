package com.project.ti2358.data.manager

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.project.ti2358.R
import com.project.ti2358.TheApplication
import com.project.ti2358.data.model.dto.Candle
import com.project.ti2358.service.*
import kotlinx.coroutines.Job
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.lang.Exception
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

@KoinApiExtension
class StrategyTazik : KoinComponent {
    private val stockManager: StockManager by inject()
    private val depositManager: DepositManager by inject()

    var stocks: MutableList<Stock> = mutableListOf()
    var stocksSelected: MutableList<Stock> = mutableListOf()

    var stocksToPurchase: MutableList<PurchaseStock> = mutableListOf()
    var stocksToPurchaseClone: MutableList<PurchaseStock> = mutableListOf()
    var stocksTickerBuyed: MutableMap<String, Double> = mutableMapOf()

    var activeJobs: MutableList<Job?> = mutableListOf()

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
        val onePiece: Double = totalMoney / SettingsManager.getTazikPurchaseParts()
        val before10 = Utils.isSessionBefore10()

        stocksToPurchase = stocksSelected.map {
            PurchaseStock(it).apply {
                percentLimitPriceChange = percent

                // отнять процент роста с начала премаркета, если мы запускаем в 10
                if (before10) {
                    val deltaPercent = it.getPriceNow() / it.getPrice0145() * 100.0 - 100.0
                    percentLimitPriceChange -= abs(deltaPercent * 0.5)
                }

                lots = (onePiece / stock.getPriceNow()).roundToInt()
                updateAbsolutePrice()
                status = PurchaseStatus.WAITING
            }
        }.toMutableList()

        // удалить все бумаги, которые уже есть в портфеле, чтобы избежать коллизий
        // удалить все бумаги, у которых 0 лотов
        stocksToPurchase.removeAll { p ->
            p.lots == 0 || depositManager.portfolioPositions.any { it.ticker == p.ticker }
        }

        stocksToPurchaseClone = stocksToPurchase.toMutableList()

        return stocksToPurchase
    }

    fun getNotificationTitle(): String {
        if (started) return "Внимание! Работает автотазик!"

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
                startStrategy()
            }

            return "Старт тазика через %02d:%02d:%02d".format(hours, minutes, seconds)
        }
    }

    @Synchronized
    fun getTotalPurchaseString(): String {
        val volume = SettingsManager.getTazikPurchaseVolume().toDouble()
        val p = SettingsManager.getTazikPurchaseParts()
        return String.format("осталось %d из %d по %.2f$, просадка %.2f", activeJobs.size, p, volume / p, basicPercentLimitPriceChange)
    }

    fun getNotificationTextShort(): String {
        val price = getTotalPurchaseString()
        var tickers = ""
        for (stock in stocksToPurchase) {
            tickers += "%s*%.2f%%".format(locale = Locale.US, stock.ticker, stock.percentLimitPriceChange)
        }

        return "$price:\n$tickers"
    }

    fun getNotificationTextLong(): String {
        stocksToPurchase.sortBy { abs(it.stock.getPriceNow() / it.tazikPrice * 100 - 100) }
        stocksToPurchase.sortBy { it.stock.getPriceNow() / it.tazikPrice * 100 - 100 }
        stocksToPurchase.sortBy { it.status }

        var tickers = ""
        for (stock in stocksToPurchase) {
            val change = (100 * stock.stock.getPriceNow()) / stock.tazikPrice - 100
            tickers += "${stock.ticker} ${stock.percentLimitPriceChange.toPercent()} = " +
                    "${stock.tazikPrice.toMoney(stock.stock)} ➡ ${stock.stock.getPriceNow().toMoney(stock.stock)} = " +
                    "${change.toPercent()} ${stock.getStatusString()}\n"
        }

        return tickers
    }

    fun prepareStrategy(scheduled : Boolean, time: String) {
        basicPercentLimitPriceChange = SettingsManager.getTazikChangePercent()

        if (!scheduled) {
            startStrategy()
            return
        }

        // подписаться только бумаги тазика, чтобы быстрее работало?
        stockManager.startTazik(stocksToPurchase.map { it.stock })

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
    private fun startStrategy() {
        fixPrice()

        activeJobs.forEach {
            try {
                if (it?.isActive == true) {
                    it.cancel()
                }
            } catch (e: Exception) {

            }
        }
        activeJobs.clear()
        stocksTickerBuyed.clear()
        started = true
    }

    @Synchronized
    fun stopStrategy() {
        started = false
        activeJobs.forEach {
            try {
                if (it?.isActive == true) {
                    it.cancel()
                }
            } catch (e: Exception) {

            }
        }
        activeJobs.clear()

        // подписаться обратно на все бумаги
        stockManager.stopTazik()
    }

    fun addBasicPercentLimitPriceChange(sign: Int) {
        basicPercentLimitPriceChange += sign * PercentLimitChangeDelta

        for (purchase in stocksToPurchase) {
            purchase.percentLimitPriceChange += sign * PercentLimitChangeDelta
            purchase.stock.candleToday?.let {
                processBuy(purchase, purchase.stock, it)
            }
        }
    }

    @Synchronized
    private fun isAllowToBuy(ticker: String, change: Double): Boolean {
        // лимит на заявки исчерпан?
        val parts = SettingsManager.getTazikPurchaseParts()
        if (activeJobs.size >= parts) return false

        // ещё не брали бумагу?
        if (ticker !in stocksTickerBuyed) {
            log("TAZIK 1: $ticker $stocksTickerBuyed ${stocksTickerBuyed[ticker]} $change")
            return true
        }

        // текущий change ниже предыдущего на 1.5x?
        if (SettingsManager.getTazikAllowAveraging()) { // разрешить усреднение?
            if (ticker in stocksTickerBuyed && stocksTickerBuyed[ticker] != 0.0 && change < stocksTickerBuyed[ticker]!! * 1.5) {
                log("TAZIK 2: $ticker $stocksTickerBuyed ${stocksTickerBuyed[ticker]} $change")
                return true
            }
        }

        return false
    }

    fun buyFirstOne() {
        for (purchase in stocksToPurchase) {
            val closingPrice = purchase.stock.candleToday?.closingPrice ?: 0.0
            if (closingPrice == 0.0) continue

            val change = closingPrice / purchase.tazikPrice * 100.0 - 100.0
            if (isAllowToBuy(purchase.ticker, change)) {
                purchase.stock.candleToday?.let {
                    processBuy(purchase, purchase.stock, it)
                }
                break
            }
        }
    }

    @Synchronized
    fun processUpdate() {
        if (!started) return

        // если стратегия стартанула и какие-то корутины уже завершились, то убрать их, чтобы появился доступ для новых покупок
        for (job in activeJobs) {
            if (job?.isActive == false) {
                activeJobs.remove(job)
                break
            }
        }
    }

    @Synchronized
    fun processStrategy(stock: Stock, candle: Candle) {
        if (!started) return

        val ticker = stock.ticker

        // если бумага не в списке скана - игнорируем
        synchronized(stocksToPurchaseClone) {
            val sorted = stocksToPurchaseClone.find { it.ticker == ticker }
            sorted?.let { purchase ->
                val change = candle.closingPrice / purchase.tazikPrice * 100.0 - 100.0
                if (purchase.tazikPrice != 0.0 &&
                    change > -50 && // защита от бага ти?
                    change <= purchase.percentLimitPriceChange && isAllowToBuy(ticker, change)
                ) {
                    processBuy(purchase, stock, candle)
                }
            }
        }
    }

    @Synchronized
    private fun processBuy(purchase: PurchaseStock, stock: Stock, candle: Candle) {
        // завершение стратегии
        val parts = SettingsManager.getTazikPurchaseParts()
        if (activeJobs.size >= parts) {
            stopStrategy()
            return
        }

        if (purchase.tazikPrice == 0.0) return

        val change = candle.closingPrice / purchase.tazikPrice * 100.0 - 100.0
        stocksTickerBuyed[stock.ticker] = change
        // просадка < x%
        log("ПРОСАДКА, БЕРЁМ! ${stock.ticker} ➡ $change ➡ ${candle.closingPrice}")

        val baseProfit = SettingsManager.getTazikTakeProfit()
        val job: Job?

        val totalMoney: Double = SettingsManager.getTazikPurchaseVolume().toDouble()
        val onePiece: Double = totalMoney / SettingsManager.getTazikPurchaseParts()
        purchase.lots = (onePiece / stock.getPriceNow()).roundToInt()

        when {
            SettingsManager.getTazikBuyAsk() -> { // покупка из аска
                job = purchase.buyLimitFromAsk(baseProfit)
            }
            SettingsManager.getTazikBuyMarket() -> { // покупка по рынку
                // примерная цена покупки (! средняя будет неизвестна из-за тинька !)
                val priceBuy = purchase.tazikPrice - abs(purchase.tazikPrice / 100.0 * purchase.percentLimitPriceChange)

                // ставим цену продажу относительно примрной средней
                var priceSell = priceBuy + priceBuy / 100.0 * baseProfit

                if (baseProfit == 0.0) priceSell = 0.0
                job = purchase.buyMarket(priceSell)
            }
            else -> { // ставим лимитку
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
                delta *= factor
                var finalProfit = baseProfit + abs(delta)

                if (baseProfit == 0.0) finalProfit = 0.0
                job = purchase.buyLimitFromBid(buyPrice, finalProfit, 1, SettingsManager.getTazikOrderLifeTimeSeconds())
            }
        }

        if (job != null) {
            activeJobs.add(job)
        }
    }
}