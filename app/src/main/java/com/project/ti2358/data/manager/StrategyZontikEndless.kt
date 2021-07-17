package com.project.ti2358.data.manager

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.project.ti2358.R
import com.project.ti2358.TheApplication
import com.project.ti2358.data.common.BrokerType
import com.project.ti2358.data.tinkoff.model.Candle
import com.project.ti2358.data.tinkoff.model.Currency
import com.project.ti2358.service.*
import kotlinx.coroutines.*
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

@KoinApiExtension
class StrategyZontikEndless : KoinComponent {
    private val stockManager: StockManager by inject()
    private val portfolioManager: PortfolioManager by inject()
    private val alorPortfolioManager: AlorPortfolioManager by inject()
    private val strategySpeaker: StrategySpeaker by inject()
    private val strategyTelegram: StrategyTelegram by inject()
    private val strategyBlacklist: StrategyBlacklist by inject()

    var stocks: MutableList<Stock> = mutableListOf()
    var stocksSelected: MutableList<Stock> = mutableListOf()

    var stocksToPurchase: MutableList<StockPurchase> = mutableListOf()
    var stocksToClonePurchase: MutableList<StockPurchase> = mutableListOf()
    var stocksTickerInProcess: MutableMap<String, Job> = ConcurrentHashMap()

    var basicPercentLimitPriceChange: Double = 0.0
    var started: Boolean = false

    var scheduledTimeStart: Calendar? = null
    var scheduledTimeEnd: Calendar? = null

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

        stocks.removeAll { it.instrument.currency == Currency.USD && it.getPrice2300() == 0.0 }
        stocks.removeAll { it.short == null }

        loadSelectedStocks(numberSet)
    }

    private suspend fun loadSelectedStocks(numberSet: Int) = withContext(StockManager.stockContext) {
        stocksSelected.clear()

        val setList: List<String> = when (numberSet) {
            1 -> SettingsManager.getZontikEndlessSet1()
            2 -> SettingsManager.getZontikEndlessSet2()
            3 -> SettingsManager.getZontikEndlessSet3()
            4 -> SettingsManager.getLoveSet()
            else -> emptyList()
        }
        stocksSelected = stocks.filter { it.ticker in setList }.toMutableList()
    }

    private suspend fun saveSelectedStocks(numberSet: Int) = withContext(StockManager.stockContext) {
        val setList = stocksSelected.map { it.ticker }.toMutableList()

        val preferences = PreferenceManager.getDefaultSharedPreferences(TheApplication.application.applicationContext)
        val editor: SharedPreferences.Editor = preferences.edit()

        val key = when (numberSet) {
            1 -> TheApplication.application.applicationContext.getString(R.string.setting_key_zontik_endless_set)
            2 -> TheApplication.application.applicationContext.getString(R.string.setting_key_zontik_endless_set_2)
            3 -> TheApplication.application.applicationContext.getString(R.string.setting_key_zontik_endless_set_3)
            4 -> TheApplication.application.applicationContext.getString(R.string.setting_key_love_set)
            else -> ""
        }

        if (key != "") {
            editor.putString(key, setList.joinToString(separator = " "))
            editor.apply()
        }
    }

    suspend fun resort(): MutableList<Stock> = withContext(StockManager.stockContext) {
        currentSort = if (currentSort == Sorting.DESCENDING) Sorting.ASCENDING else Sorting.DESCENDING
        stocks.sortBy {
            val sign = if (currentSort == Sorting.ASCENDING) 1 else -1
            val multiplier = if (it in stocksSelected) 100 else 1
            val final = it.changePrice2300DayPercent * sign - multiplier
            if (final.isNaN()) 0.0 else final
        }
        return@withContext stocks
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

    suspend fun getPurchaseStock(): MutableList<StockPurchase> = withContext(StockManager.stockContext) {
        if (started) return@withContext stocksToPurchase

        val percent = SettingsManager.getZontikEndlessChangePercent()

        val totalMoneyTinkoff: Double = SettingsManager.getZontikEndlessPurchaseVolume().toDouble()
        var onePieceTinkoff: Double = totalMoneyTinkoff / SettingsManager.getZontikEndlessPurchaseParts()

        // TODO:
        val totalMoneyAlor: Double = 100.0//SettingsManager.getZontikEndlessPurchaseVolume().toDouble()
        var onePieceAlor: Double = totalMoneyAlor / 2 // totalMoneyAlor / SettingsManager.getTazikPurchaseParts()

        val purchases: MutableList<StockPurchase> = mutableListOf()
        for (stock in stocksSelected) {
            if (SettingsManager.getBrokerTinkoff()) {
                val purchaseTinkoff = StockPurchaseTinkoff(stock)
                purchases.add(purchaseTinkoff)
            }

            if (SettingsManager.getBrokerAlor()) {
                val purchaseAlor = StockPurchaseAlor(stock)
                purchases.add(purchaseAlor)
            }
        }

        purchases.forEach {
            it.percentLimitPriceChange = percent

            // посчитать количество лотов на каждую бумагу/брокера
            val part = when (it.broker) {
                BrokerType.TINKOFF -> if (it.stock.instrument.currency == Currency.RUB) onePieceTinkoff * Utils.getUSDRUB() else onePieceTinkoff
                BrokerType.ALOR -> if (it.stock.instrument.currency == Currency.RUB) onePieceAlor * Utils.getUSDRUB() else onePieceAlor
            }

            if (it.stock.getPriceNow() != 0.0) {
                it.lots = (part / it.stock.getPriceNow()).roundToInt()
            }
            it.updateAbsolutePrice()
            it.status = PurchaseStatus.WAITING
        }

        stocksToPurchase = Collections.synchronizedList(purchases)

        // удалить все бумаги, у которых 0 лотов = не хватает на покупку одной части
        stocksToPurchase.removeAll { it.lots == 0 || it.lots > 99999999 }

        // удалить все бумаги, у которых недавно или скоро отчёты
        if (SettingsManager.getZontikEndlessExcludeReports()) {
            stocksToPurchase.removeAll { it.stock.report != null }
        }

        // удалить все бумаги, у которых скоро дивы
        if (SettingsManager.getZontikEndlessExcludeDivs()) {
            stocksToPurchase.removeAll { it.stock.dividend != null }
        }

        // удалить все бумаги, у которых скоро FDA фаза
        if (SettingsManager.getZontikEndlessExcludeFDA()) {
            stocksToPurchase.removeAll { it.stock.fda != null }
        }

        // удалить все бумаги, которые уже есть в портфеле, чтобы избежать коллизий
        if (SettingsManager.getZontikEndlessExcludeDepo()) {
            stocksToPurchase.removeAll { p -> portfolioManager.portfolioPositions.any { it.ticker == p.ticker && p.broker == BrokerType.TINKOFF } }
            stocksToPurchase.removeAll { p -> alorPortfolioManager.portfolioPositions.any { it.symbol == p.ticker && p.broker == BrokerType.ALOR } }
        }

        // удалить все бумаги из чёрного списка
        val blacklist = strategyBlacklist.getBlacklistStocks()
        stocksToPurchase.removeAll { it.ticker in blacklist.map { stock -> stock.ticker } }

        stocksToClonePurchase = stocksToPurchase.toMutableList()

        return@withContext stocksToPurchase
    }

    fun getNotificationTitle(): String = runBlocking(StockManager.stockContext) {
        if (started) {
            if (scheduledTimeEnd != null) {
                val now = Calendar.getInstance(TimeZone.getDefault())
                val current = scheduledTimeEnd?.timeInMillis ?: 0
                val scheduleDelay = current - now.timeInMillis

                val allSeconds = scheduleDelay / 1000
                val hours = allSeconds / 3600
                val minutes = (allSeconds - hours * 3600) / 60
                val seconds = allSeconds % 60

                fixPrice()
                if (hours + minutes + seconds <= 0) {
                    stopStrategyCommand()
                }

                return@runBlocking "Работает ☂️! Финиш через %02d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                return@runBlocking "Работает бесконечный ☂️!"
            }
        }

        if (scheduledTimeStart != null) {
            val now = Calendar.getInstance(TimeZone.getDefault())
            val current = scheduledTimeStart?.timeInMillis ?: 0
            val scheduleDelay = current - now.timeInMillis

            val allSeconds = scheduleDelay / 1000
            val hours = allSeconds / 3600
            val minutes = (allSeconds - hours * 3600) / 60
            val seconds = allSeconds % 60

            fixPrice()
            if (hours + minutes + seconds <= 0) {
                startStrategy(true)
            }

            return@runBlocking "Старт ☂️ через %02d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            return@runBlocking "Бесконечный ☂️ приостановлен"
        }
    }

    fun getTotalPurchaseString(): String {
        val volume = SettingsManager.getZontikEndlessPurchaseVolume().toDouble()
        val p = SettingsManager.getZontikEndlessPurchaseParts()
        val volumeShares = SettingsManager.getZontikEndlessMinVolume()
        return String.format(
            "%d из %d по %.2f$, просадка %.2f / %.2f / %.2f / %d",
            stocksTickerInProcess.size,
            p,
            volume / p,
            basicPercentLimitPriceChange,
            SettingsManager.getZontikEndlessTakeProfit(),
            SettingsManager.getZontikEndlessApproximationFactor(),
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

    fun getSortedPurchases(): List<StockPurchase> {
        currentPurchaseSort = if (currentPurchaseSort == Sorting.DESCENDING) Sorting.ASCENDING else Sorting.DESCENDING

        val local = stocksToPurchase.toMutableList()
        local.removeAll { it.zontikEndlessPrice == 0.0 }

        if (currentPurchaseSort == Sorting.ASCENDING) {
            local.sortBy { it.stock.getPriceNow() / it.zontikEndlessPrice * 100 - 100 }
        } else {
            local.sortByDescending { it.stock.getPriceNow() / it.zontikEndlessPrice * 100 - 100 }
        }

        return local
    }

    fun getNotificationTextLong(): String {
        val stocks = stocksToPurchase.map {
            Pair(it.stock.getPriceNow(), it)
        }.sortedByDescending {
            it.first / it.second.zontikEndlessPrice * 100 - 100
        }

        var tickers = ""
        for (pair in stocks) {
            val purchase = pair.second
            val priceNow = pair.first

            val change = (100 * priceNow) / purchase.zontikEndlessPrice - 100
            if (change <= 0.01 && purchase.status == PurchaseStatus.WAITING && stocksToPurchase.size > 5) continue

            var vol = 0
            if (purchase.stock.minuteCandles.isNotEmpty()) {
                vol = purchase.stock.minuteCandles.last().volume
            }
            tickers += "${purchase.ticker} ${purchase.percentLimitPriceChange.toPercent()} = " +
                    "${purchase.zontikEndlessPrice.toMoney(purchase.stock)} ➡ ${priceNow.toMoney(purchase.stock)} = " +
                    "${change.toPercent()} ${purchase.getStatusString()} v=${vol}\n"
        }
        if (tickers == "") tickers = "отображаются положительные бумаги ⏳"

        return tickers
    }

    private fun fixPrice() {
        // зафикировать цену, чтобы change считать от неё
        for (purchase in stocksToClonePurchase) {
            purchase.zontikEndlessPrice = purchase.stock.getPriceNow()
        }
    }

    suspend fun restartStrategy(newPercent: Double = 0.0, profit: Double = 0.0) = withContext(StockManager.stockContext) {
        if (started) stopStrategy()

        if (newPercent != 0.0) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(TheApplication.application.applicationContext)
            val editor: SharedPreferences.Editor = preferences.edit()
            val key = TheApplication.application.applicationContext.getString(R.string.setting_key_zontik_endless_min_percent_to_buy)
            editor.putString(key, "%.2f".format(locale = Locale.US, newPercent))
            editor.apply()
        }

        val preferences = PreferenceManager.getDefaultSharedPreferences(TheApplication.application.applicationContext)
        val editor: SharedPreferences.Editor = preferences.edit()
        val key = TheApplication.application.applicationContext.getString(R.string.setting_key_zontik_endless_take_profit)
        editor.putString(key, "%.2f".format(locale = Locale.US, profit))
        editor.apply()

        process(1)
        getPurchaseStock()
        delay(500)

        Utils.startService(TheApplication.application.applicationContext, StrategyZontikEndlessService::class.java)
        startStrategy(false)
    }

    suspend fun stopStrategyCommand() = withContext(StockManager.stockContext) {
        Utils.stopService(TheApplication.application.applicationContext, StrategyZontikEndlessService::class.java)
    }

    fun prepareStrategy(scheduled : Boolean, timeFromTo: Pair<String, String>) = runBlocking (StockManager.stockContext) {
        basicPercentLimitPriceChange = SettingsManager.getZontikEndlessChangePercent()

        if (!scheduled) {
            scheduledTimeStart = null
            scheduledTimeEnd = null

            startStrategy(scheduled)
            return@runBlocking
        }

        started = false

        val differenceHours: Int = Utils.getTimeDiffBetweenMSK()
        if (timeFromTo.first != "") { // старт таза
            val dayTimeStart = timeFromTo.first.split(":").toTypedArray()
            if (dayTimeStart.size < 3) {
                GlobalScope.launch(Dispatchers.Main) {
                    Utils.showToastAlert("Неверный формат времени старта $dayTimeStart")
                }
                return@runBlocking
            }

            val hours = Integer.parseInt(dayTimeStart[0])
            val minutes = Integer.parseInt(dayTimeStart[1])
            val seconds = Integer.parseInt(dayTimeStart[2])

            scheduledTimeStart = Calendar.getInstance(TimeZone.getDefault())
            scheduledTimeStart?.let {
                it.add(Calendar.HOUR_OF_DAY, -differenceHours)
                it.set(Calendar.HOUR_OF_DAY, hours)
                it.set(Calendar.MINUTE, minutes)
                it.set(Calendar.SECOND, seconds)
                it.add(Calendar.HOUR_OF_DAY, differenceHours)

                val now = Calendar.getInstance(TimeZone.getDefault())
                val scheduleDelay = it.timeInMillis - now.timeInMillis
                if (scheduleDelay < 0) {
                    GlobalScope.launch(Dispatchers.Main) {
                        Utils.showToastAlert("Ошибка! Отрицательное время!? втф = $scheduleDelay")
                    }
                }
            }
        }

        if (timeFromTo.second != "") { // старт таза
            val dayTimeEnd = timeFromTo.second.split(":").toTypedArray()
            if (dayTimeEnd.size < 3) {
                GlobalScope.launch(Dispatchers.Main) {
                    Utils.showToastAlert("Неверный формат времени финиша $dayTimeEnd")
                }
                return@runBlocking
            }

            val hours = Integer.parseInt(dayTimeEnd[0])
            val minutes = Integer.parseInt(dayTimeEnd[1])
            val seconds = Integer.parseInt(dayTimeEnd[2])

            scheduledTimeEnd = Calendar.getInstance(TimeZone.getDefault())
            scheduledTimeEnd?.let {
                it.add(Calendar.HOUR_OF_DAY, -differenceHours)
                it.set(Calendar.HOUR_OF_DAY, hours)
                it.set(Calendar.MINUTE, minutes)
                it.set(Calendar.SECOND, seconds)
                it.add(Calendar.HOUR_OF_DAY, differenceHours)

                val now = Calendar.getInstance(TimeZone.getDefault())
                val scheduleDelay = it.timeInMillis - now.timeInMillis
                if (scheduleDelay < 0) {
                    GlobalScope.launch(Dispatchers.Main) {
                        Utils.showToastAlert("Ошибка! Отрицательное время!? втф = $scheduleDelay")
                    }
                }
            }
        }
    }

    suspend fun startStrategy(scheduled: Boolean) = withContext(StockManager.stockContext) {
        basicPercentLimitPriceChange = SettingsManager.getZontikEndlessChangePercent()

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
                val seconds = SettingsManager.getZontikEndlessResetIntervalSeconds().toLong()
                delay(1000 * seconds)
                fixPrice()
            }
        }

        strategyTelegram.sendZontikEndlessStart(true)
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
        strategyTelegram.sendZontikEndlessStart(false)
    }

    fun addBasicPercentLimitPriceChange(sign: Int) = runBlocking (StockManager.stockContext) {
        basicPercentLimitPriceChange += sign * PercentLimitChangeDelta

        val preferences = PreferenceManager.getDefaultSharedPreferences(TheApplication.application.applicationContext)
        val editor: SharedPreferences.Editor = preferences.edit()
        val key = TheApplication.application.applicationContext.getString(R.string.setting_key_zontik_endless_min_percent_to_buy)
        editor.putString(key, "%.2f".format(locale = Locale.US, basicPercentLimitPriceChange))
        editor.apply()

        for (purchase in stocksToPurchase) {
            purchase.percentLimitPriceChange += sign * PercentLimitChangeDelta
            if (purchase.stock.minuteCandles.isNotEmpty()) {
                processStrategy(purchase.stock, purchase.stock.minuteCandles.last())
            }
        }
    }

    private fun isAllowToSell(purchase: StockPurchase, change: Double, volume: Int): Boolean {
        if (purchase.zontikEndlessPrice == 0.0 ||                   // стартовая цена нулевая = не загрузились цены
            abs(change) > 50 ||                                     // конечная цена нулевая или просто огромная просадка
            change < 0 ||                                           // изменение ОТРИЦАТЕЛЬНОЕ
            change < purchase.percentLimitPriceChange ||            // изменение не в пределах наших настроек  1 < 2
            volume < SettingsManager.getZontikEndlessMinVolume() || // если объём свечи меньше настроек
            purchase.stock.getTodayVolume() < SettingsManager.getZontikEndlessDayMinVolume() // дневной объём меньше, чем нужно
        ) {
            return false
        }

        val ticker = purchase.ticker

        // лимит на заявки исчерпан?
        if (stocksTickerInProcess.size >= SettingsManager.getZontikEndlessPurchaseParts()) return false

        // проверить, если бумага в депо и усреднение отключено, то запретить тарить
        if (portfolioManager.portfolioPositions.find { it.ticker == purchase.ticker } != null && !SettingsManager.getZontikEndlessAllowAveraging()) {
            return false
        }

        // ещё не брали бумагу?
        if (ticker !in stocksTickerInProcess) {
            return true
        }

        // разрешить усреднение?
        if (SettingsManager.getZontikEndlessAllowAveraging()) {
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
        if (stock in strategyBlacklist.getBlacklistStocks()) return

        val ticker = stock.ticker

        // если бумага не в списке скана - игнорируем
        val sorted = stocksToClonePurchase.find { it.ticker == ticker }
        sorted?.let { purchase ->
            val change = candle.closingPrice / purchase.zontikEndlessPrice * 100.0 - 100.0
            val volume = candle.volume

            if (isAllowToSell(purchase, change, volume)) {
                processSell(purchase, stock, candle)
            }
        }
    }

    private fun processSell(purchase: StockPurchase, stock: Stock, candle: Candle) {
        // завершение стратегии
        val parts = SettingsManager.getZontikEndlessPurchaseParts()
        if (stocksTickerInProcess.size >= parts) { // останавливить стратегию автоматически
            stopStrategy()
            return
        }

        if (purchase.zontikEndlessPrice == 0.0) return

        val change = candle.closingPrice / purchase.zontikEndlessPrice * 100.0 - 100.0

        // ищем цену максимально близкую к просадке
        var delta = abs(change) - abs(purchase.percentLimitPriceChange)

        // 0.80 коэф приближения к верхней точке, в самом верху мало шансов
        delta *= SettingsManager.getZontikEndlessApproximationFactor()

        // корректируем % роста для шорта
        val percent = abs(purchase.percentLimitPriceChange) + delta

        // вычислияем финальную цену лимитки
        var sellPrice = purchase.zontikEndlessPrice + abs(purchase.zontikEndlessPrice / 100.0 * percent)

        // защита от спайков - сколько минут цена была выше цены покупки, начиная с предыдущей
        var minutes = SettingsManager.getZontikEndlessSpikeProtection()
        if (purchase.stock.minuteCandles.size >= minutes) { // не считать спайки на открытии и на старте таза - мало доступных свечей
            for (i in purchase.stock.minuteCandles.indices.reversed()) {

                // пропустить текущую свечу, по которой у нас просадка
                if (i == purchase.stock.minuteCandles.size - 1) continue

                // проверить цены закрытия нескольких предыдущих свечей, нужно чтобы они были ниже
                if (purchase.stock.minuteCandles[i].closingPrice < sellPrice) { // если цена ниже, отнимаем счётчик, проверяем дальше
                    minutes--

                    // если несколько свечей подряд с ценой выше, то всё ок - тарим!
                    if (minutes == 0) {
                        break
                    }
                } else { // был спайк на несколько свечек - тарить опасно!
                    // обновить цену, чтобы не затарить на следующей свече, возможен нож ступенькой
                    purchase.zontikEndlessPrice = candle.closingPrice
                    strategySpeaker.speakZontikSpikeSkip(purchase, change)
                    strategyTelegram.sendZontikSpike(
                        purchase,
                        sellPrice,
                        purchase.zontikEndlessPrice,
                        candle.closingPrice,
                        change,
                        stocksTickerInProcess.size,
                        parts
                    )
                    return
                }
            }
        }

        // проверка на цену закрытия (выше не тарить)
        if (SettingsManager.getZontikEndlessClosePriceProtectionPercent() != 0.0) {
            if (stock.instrument.currency == Currency.USD) {
                val finalPrice = stock.getPrice2300() + stock.getPrice2300() / 100.0 * SettingsManager.getZontikEndlessClosePriceProtectionPercent()
                if (sellPrice <= finalPrice) {
                    return
                }
            } else {
                if (sellPrice >= stock.getPrice1000()) {
                    return
                }
            }
        }

        // вычисляем процент профита после сдвига лимитки ниже
        var finalProfit = SettingsManager.getZontikEndlessTakeProfit()

        // если мы усредняем, то не нужно выставлять ТП, потому что неизвестно какие заявки из усреднения выполнятся и какая будет в итоге средняя
        if (stock.ticker in stocksTickerInProcess && SettingsManager.getZontikEndlessAllowAveraging()) {
            finalProfit = 0.0
        }

        sellPrice = Utils.makeNicePrice(sellPrice, stock)
        val job = purchase.sellLimitFromAsk(sellPrice, finalProfit, 1, SettingsManager.getZontikEndlessOrderLifeTimeSeconds())
        if (job != null) {
            stocksTickerInProcess[stock.ticker] = job

            var buyPrice = sellPrice - sellPrice / 100.0 * finalProfit
            buyPrice = Utils.makeNicePrice(buyPrice, stock)

            strategySpeaker.speakZontik(purchase, change)
            strategyTelegram.sendZontikSell(purchase, sellPrice, buyPrice, purchase.zontikEndlessPrice, candle.closingPrice, change, stocksTickerInProcess.size, parts)
            purchase.zontikEndlessPrice = candle.closingPrice
        }
    }
}