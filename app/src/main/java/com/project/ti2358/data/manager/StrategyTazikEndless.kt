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
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

@KoinApiExtension
class StrategyTazikEndless : KoinComponent {
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

    var jobResetPrice: Job? = null

    var currentSort: Sorting = Sorting.DESCENDING
    var currentPurchaseSort: Sorting = Sorting.DESCENDING

    companion object {
        const val PercentLimitChangeDelta = 0.05
    }

    suspend fun process(numberSet: Int) = withContext(StockManager.stockContext) {
        val all = stockManager.getWhiteStocks()
        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        stocks = all.filter { (it.getPriceNow() > min && it.getPriceNow() < max) || it.getPriceNow() == 0.0 }.toMutableList()
        stocks.sortBy { it.changePrice2300DayPercent }
        loadSelectedStocks(numberSet)
    }

    private fun loadSelectedStocks(numberSet: Int) {
        stocksSelected.clear()

        val setList: List<String> = when (numberSet) {
            1 -> SettingsManager.getTazikEndlessSet1()
            2 -> SettingsManager.getTazikEndlessSet2()
            3 -> SettingsManager.getTazikEndlessSet3()
            4 -> SettingsManager.getLoveSet()
            else -> emptyList()
        }
        stocksSelected = stocks.filter { it.ticker in setList }.toMutableList()
    }

    private fun saveSelectedStocks(numberSet: Int) {
        val setList = stocksSelected.map { it.ticker }.toMutableList()

        val preferences = PreferenceManager.getDefaultSharedPreferences(TheApplication.application.applicationContext)
        val editor: SharedPreferences.Editor = preferences.edit()

        val key = when (numberSet) {
            1 -> TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_endless_set)
            2 -> TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_endless_set_2)
            3 -> TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_endless_set_3)
            4 -> TheApplication.application.applicationContext.getString(R.string.setting_key_love_set)
            else -> ""
        }

        if (key != "") {
            editor.putString(key, setList.joinToString(separator = " "))
            editor.apply()
        }
    }

    fun resort(): MutableList<Stock> {
        currentSort = if (currentSort == Sorting.DESCENDING) Sorting.ASCENDING else Sorting.DESCENDING
        stocks.sortBy {
            val sign = if (currentSort == Sorting.ASCENDING) 1 else -1
            val multiplier = if (it in stocksSelected) 100 else 1
            val final = it.changePrice2300DayPercent * sign - multiplier
            if (final.isNaN()) 0.0 else final
        }
        return stocks
    }

    suspend fun setSelected(stock: Stock, value: Boolean, numberSet: Int) = withContext(StockManager.stockContext) {
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

    suspend fun getPurchaseStock(): MutableList<PurchaseStock> = withContext(StockManager.stockContext) {
        if (started) return@withContext stocksToPurchase

        val percent = SettingsManager.getTazikEndlessChangePercent()
        val totalMoney: Double = SettingsManager.getTazikEndlessPurchaseVolume().toDouble()
        val onePiece: Double = totalMoney / SettingsManager.getTazikEndlessPurchaseParts()

        val purchases: MutableList<PurchaseStock> = mutableListOf()
        for (stock in stocksSelected) {
            val purchase = PurchaseStock(stock)
            for (p in stocksToPurchase) {
                if (p.ticker == stock.ticker) {
                    purchase.apply {
                        percentLimitPriceChange = p.percentLimitPriceChange
                    }
                    break
                }
            }
            purchases.add(purchase)
        }
        stocksToPurchase = purchases
        stocksToPurchase.forEach {
            it.percentLimitPriceChange = percent

            val total = if (it.stock.instrument.currency == Currency.RUB) onePiece * Utils.getUSDRUB() else onePiece

            if (it.stock.getPriceNow() != 0.0) {
                it.lots = (total / it.stock.getPriceNow()).roundToInt()
            }
            it.updateAbsolutePrice()
            it.status = PurchaseStatus.WAITING
        }

        // удалить все бумаги, у которых 0 лотов = не хватает на покупку одной части
        stocksToPurchase.removeAll { it.lots == 0 || it.lots > 99999999 }

        // удалить все бумаги, у которых недавно или скоро отчёты
        if (SettingsManager.getTazikEndlessExcludeReports()) {
            stocksToPurchase.removeAll { it.stock.report != null }
        }

        // удалить все бумаги, у которых скоро дивы
        if (SettingsManager.getTazikEndlessExcludeDivs()) {
            stocksToPurchase.removeAll { it.stock.dividend != null }
        }

        // удалить все бумаги, у которых скоро FDA фаза
        if (SettingsManager.getTazikEndlessExcludeFDA()) {
            stocksToPurchase.removeAll { it.stock.fda != null }
        }

        // удалить все бумаги, которые уже есть в портфеле, чтобы избежать коллизий
        if (SettingsManager.getTazikEndlessExcludeDepo()) {
            stocksToPurchase.removeAll { p -> depositManager.portfolioPositions.any { it.ticker == p.ticker } }
        }

        // удалить все бумаги из чёрного списка
        val blacklist = strategyBlacklist.getBlacklistStocks()
        stocksToPurchase.removeAll { it.ticker in blacklist.map { stock -> stock.ticker } }

        stocksToPurchaseClone = stocksToPurchase.toMutableList()

        return@withContext stocksToPurchase
    }

    fun getNotificationTitle(): String {
        if (started) return "Работает бесконечный таз!"

        return "Бесконечный таз приостановлен"
    }

    fun getTotalPurchaseString(): String {
        val volume = SettingsManager.getTazikEndlessPurchaseVolume().toDouble()
        val p = SettingsManager.getTazikEndlessPurchaseParts()
        val volumeShares = SettingsManager.getTazikEndlessMinVolume()
        return String.format(
            "%d из %d по %.2f$, просадка %.2f / %.2f / %.2f / %d",
            stocksTickerInProcess.size,
            p,
            volume / p,
            basicPercentLimitPriceChange,
            SettingsManager.getTazikEndlessTakeProfit(),
            SettingsManager.getTazikEndlessApproximationFactor(),
            volumeShares
        )
    }

    fun getNotificationTextShort(): String {
        val price = getTotalPurchaseString()
        var tickers = ""
        for (stock in stocksToPurchase) {
            tickers += "${stock.ticker} "
        }
        return "$price:\n$tickers"
    }

    fun getSortedPurchases(): List<PurchaseStock> {
        currentPurchaseSort = if (currentPurchaseSort == Sorting.DESCENDING) Sorting.ASCENDING else Sorting.DESCENDING

        val local = stocksToPurchase.toMutableList()
        local.removeAll { it.tazikEndlessPrice == 0.0 }

        val volume = SettingsManager.getTazikEndlessMinVolume()
        if (currentPurchaseSort == Sorting.ASCENDING) {
            local.sortBy { it.stock.getPriceNow(volume) / it.tazikEndlessPrice * 100 - 100 }
        } else {
            local.sortByDescending { it.stock.getPriceNow(volume) / it.tazikEndlessPrice * 100 - 100 }
        }

        return local
    }

    fun getNotificationTextLong(): String {
        val volume = SettingsManager.getTazikEndlessMinVolume()

        val stocks = stocksToPurchase.map {
            Pair(it.stock.getPriceNow(volume, true), it)
        }.sortedBy {
            it.first / it.second.tazikEndlessPrice * 100 - 100
        }

        var tickers = ""
        for (pair in stocks) {
            val purchase = pair.second
            val priceNow = pair.first

            val change = (100 * priceNow) / purchase.tazikEndlessPrice - 100
            if (change >= -0.01 && purchase.status == PurchaseStatus.WAITING && stocksToPurchase.size > 5) continue

            var vol = 0
            if (purchase.stock.minuteCandles.isNotEmpty()) {
                vol = purchase.stock.minuteCandles.last().volume
            }
            tickers += "${purchase.ticker} ${purchase.percentLimitPriceChange.toPercent()} = " +
                    "${purchase.tazikEndlessPrice.toMoney(purchase.stock)} ➡ ${priceNow.toMoney(purchase.stock)} = " +
                    "${change.toPercent()} ${purchase.getStatusString()} v=${vol}\n"
        }
        if (tickers == "") tickers = "только отрицательные бумаги ⏳"

        return tickers
    }

    private fun fixPrice() {
        // зафикировать цену, чтобы change считать от неё
        for (purchase in stocksToPurchaseClone) {
            purchase.tazikEndlessPrice = purchase.stock.getPriceNow(SettingsManager.getTazikEndlessMinVolume(), true)
        }
    }

    suspend fun restartStrategy(newPercent: Double = 0.0) = withContext(StockManager.stockContext) {
        if (started) stopStrategy()

        if (newPercent != 0.0) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(TheApplication.application.applicationContext)
            val editor: SharedPreferences.Editor = preferences.edit()
            val key = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_endless_min_percent_to_buy)
            editor.putString(key, "%.2f".format(newPercent))
            editor.apply()
        }

        getPurchaseStock()
        delay(500)
        startStrategy()
    }

    suspend fun startStrategy() = withContext(StockManager.stockContext) {
        basicPercentLimitPriceChange = SettingsManager.getTazikEndlessChangePercent()

        fixPrice()

        stocksTickerInProcess.forEach {
            try {
                if (it.value.isActive) {
                    it.value.cancel()
                }
            } catch (e: Exception) {

            }
        }
        stocksTickerInProcess.clear()
        started = true

        jobResetPrice?.cancel()
        jobResetPrice = GlobalScope.launch(Dispatchers.Main) {
            while (true) {
                val seconds = SettingsManager.getTazikEndlessResetIntervalSeconds().toLong()
                delay(1000 * seconds)
                fixPrice()
            }
        }

        strategyTelegram.sendTazikEndlessStart(true)
    }

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
        jobResetPrice?.cancel()
        strategyTelegram.sendTazikEndlessStart(false)
    }

    fun addBasicPercentLimitPriceChange(sign: Int) = runBlocking (StockManager.stockContext) {
        basicPercentLimitPriceChange += sign * PercentLimitChangeDelta

        for (purchase in stocksToPurchase) {
            purchase.percentLimitPriceChange += sign * PercentLimitChangeDelta
            if (purchase.stock.minuteCandles.isNotEmpty()) {
                processStrategy(purchase.stock, purchase.stock.minuteCandles.last())
            }
        }
    }

    private fun isAllowToBuy(purchase: PurchaseStock, change: Double, volume: Int): Boolean {
        if (purchase.tazikEndlessPrice == 0.0 ||                    // стартовая цена нулевая = не загрузились цены
            abs(change) > 50 ||                                     // конечная цена нулевая или просто огромная просадка
            change > 0 ||                                           // изменение положительное
            change > purchase.percentLimitPriceChange ||            // изменение не в пределах наших настроек
            volume < SettingsManager.getTazikEndlessMinVolume() ||  // если объём свечи меньше настроек
            purchase.stock.getTodayVolume() < SettingsManager.getTazikEndlessDayMinVolume() // дневной объём меньше, чем нужно
        ) {
            return false
        }

        val ticker = purchase.ticker

        // лимит на заявки исчерпан?
        if (stocksTickerInProcess.size >= SettingsManager.getTazikEndlessPurchaseParts()) return false

        // проверить, если бумага в депо и усреднение отключено, то запретить тарить
        if (depositManager.portfolioPositions.find { it.ticker == purchase.ticker } != null && !SettingsManager.getTazikEndlessAllowAveraging()) {
            return false
        }

        // ещё не брали бумагу?
        if (ticker !in stocksTickerInProcess) {
            return true
        }

        // разрешить усреднение?
        if (SettingsManager.getTazikEndlessAllowAveraging()) {
            return true
        }

        return false
    }

    fun processUpdate() = runBlocking(StockManager.stockContext) {
        if (!started) return@runBlocking

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
        val sorted = stocksToPurchaseClone.find { it.ticker == ticker }
        sorted?.let { purchase ->
            val change = candle.closingPrice / purchase.tazikEndlessPrice * 100.0 - 100.0
            val volume = candle.volume

            if (isAllowToBuy(purchase, change, volume)) {
                processBuy(purchase, stock, candle)
            }
        }
    }

    private fun processBuy(purchase: PurchaseStock, stock: Stock, candle: Candle) {
        // завершение стратегии
        val parts = SettingsManager.getTazikEndlessPurchaseParts()
        if (stocksTickerInProcess.size >= parts) { // останавливить стратегию автоматически
            stopStrategy()
            return
        }

        if (purchase.tazikEndlessPrice == 0.0) return

        val change = candle.closingPrice / purchase.tazikEndlessPrice * 100.0 - 100.0

        // ищем цену максимально близкую к просадке
        var delta = abs(change) - abs(purchase.percentLimitPriceChange)

        // 0.80 коэф приближения к нижней точке, в самом низу могут не налить
        delta *= SettingsManager.getTazikEndlessApproximationFactor()

        // корректируем % падения для покупки
        val percent = abs(purchase.percentLimitPriceChange) + delta

        // вычислияем финальную цену лимитки
        var buyPrice = purchase.tazikEndlessPrice - abs(purchase.tazikEndlessPrice / 100.0 * percent)

        // защита от спайков - сколько минут цена была выше цены покупки, начиная с предыдущей
        var minutes = SettingsManager.getTazikEndlessSpikeProtection()
        for (i in purchase.stock.minuteCandles.indices.reversed()) {

            // пропустить текущую свечу, по которой у нас просадка
            if (i == purchase.stock.minuteCandles.size - 1) continue

            // проверить цены закрытия нескольких предыдущих свечей
            if (purchase.stock.minuteCandles[i].closingPrice > buyPrice) { // если цена выше, отнимаем счётчик, проверяем дальше
                minutes--

                // если несколько свечей подряд с ценой выше, то всё ок - тарим!
                if (minutes == 0) {
                    break
                }
            } else { // был спайк на несколько свечек - тарить опасно!
                // обновить цену, чтобы не затарить на следующей свече, возможен нож ступенькой
                purchase.tazikEndlessPrice = candle.closingPrice
                strategySpeaker.speakTazikSpikeSkip(purchase, change)
                strategyTelegram.sendTazikSpike(purchase, buyPrice, purchase.tazikEndlessPrice, candle.closingPrice, change, stocksTickerInProcess.size, parts)
                return
            }
        }

        // проверка на цену закрытия (выше не тарить)
        if (SettingsManager.getTazikEndlessClosePriceProtection()) {
            if (stock.instrument.currency == Currency.USD) {
                if (buyPrice >= stock.getPrice2300()) {
                    return
                }
            } else {
                if (buyPrice >= stock.getPrice1000()) {
                    return
                }
            }
        }

        // вычисляем процент профита после сдвига лимитки ниже
        var finalProfit = SettingsManager.getTazikEndlessTakeProfit()

        // если мы усредняем, то не нужно выставлять ТП, потому что неизвестно какие заявки из усреднения выполнятся и какая будет в итоге средняя
        if (stock.ticker in stocksTickerInProcess && SettingsManager.getTazikEndlessAllowAveraging()) {
            finalProfit = 0.0
        }

        buyPrice = Utils.makeNicePrice(buyPrice, stock)
        val job = purchase.buyLimitFromBid(buyPrice, finalProfit, 1, SettingsManager.getTazikEndlessOrderLifeTimeSeconds())
        if (job != null) {
            stocksTickerInProcess[stock.ticker] = job

            var sellPrice = buyPrice + buyPrice / 100.0 * finalProfit
            sellPrice = Utils.makeNicePrice(sellPrice, stock)

            strategySpeaker.speakTazik(purchase, change)
            strategyTelegram.sendTazikBuy(purchase, buyPrice, sellPrice, purchase.tazikEndlessPrice, candle.closingPrice, change, stocksTickerInProcess.size, parts)
            purchase.tazikEndlessPrice = candle.closingPrice
        }
    }
}