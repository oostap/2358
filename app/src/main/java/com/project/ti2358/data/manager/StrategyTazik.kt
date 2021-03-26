package com.project.ti2358.data.manager

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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

    private val keySavedSelectedStock: String = "tazik_selected"

    var stocks: MutableList<Stock> = mutableListOf()
    var stocksSelected: MutableList<Stock> = mutableListOf()

    var stocksToPurchase: MutableList<PurchaseStock> = mutableListOf()
    var stocksTickerBuyed: MutableMap<String, Double> = mutableMapOf()

    var activeJobs: MutableList<Job?> = mutableListOf()

    var basicPercentLimitPriceChange: Double = 0.0
    var started: Boolean = false

    var scheduledStartTime: Calendar? = null
    var currentSort: Sorting = Sorting.DESCENDING
    private val gson = Gson()

    companion object {
        const val PercentLimitChangeDelta = 0.05
    }

    fun process(numberSet: Int): MutableList<Stock> {
        val all = stockManager.getWhiteStocks()
        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        stocks = all.filter { (it.getPriceDouble() > min && it.getPriceDouble() < max) || it.getPriceDouble() == 0.0 }.toMutableList()
        stocks.sortBy { it.changePrice2359DayPercent }
        loadSelectedStocks(numberSet)
        return stocks
    }

    private fun loadSelectedStocks(numberSet: Int) {
        stocksSelected.clear()

        val key = "${keySavedSelectedStock}_$numberSet"
        val jsonStocks = PreferenceManager.getDefaultSharedPreferences(TheApplication.application.applicationContext).getString(key, null)
        jsonStocks?.let {
            val itemType = object : TypeToken<List<String>>() {}.type
            val stocksSelectedList: List<String> = gson.fromJson(jsonStocks, itemType)
            stocksSelected = stocks.filter { it.ticker in stocksSelectedList }.toMutableList()
        }
    }

    private fun saveSelectedStocks(numberSet: Int) {
        val list = stocksSelected.map { it.ticker }.toMutableList()

        val preferences = PreferenceManager.getDefaultSharedPreferences(TheApplication.application.applicationContext)
        val editor: SharedPreferences.Editor = preferences.edit()
        val data = gson.toJson(list)
        val key = "${keySavedSelectedStock}_$numberSet"
        editor.putString(key, data)
        editor.apply()
    }

    fun resort(): MutableList<Stock> {
        currentSort = if (currentSort == Sorting.DESCENDING) Sorting.ASCENDING else Sorting.DESCENDING
        stocks.sortBy {
            val sign = if (currentSort == Sorting.ASCENDING) 1 else -1
            val multiplier = if (it in stocksSelected) 100 else 1
            it.changePrice2359DayPercent * sign - multiplier
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
        stocksSelected.sortBy { it.changePrice2359DayPercent }

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

        stocksToPurchase = stocksSelected.map {
            PurchaseStock(it).apply {
                percentLimitPriceChange = percent
                lots = (onePiece / stock.getPriceDouble()).roundToInt()
                updateAbsolutePrice()
                status = OrderStatus.WAITING
            }
        }.toMutableList()

        // удалить все бумаги, которые уже есть в портфеле, чтобы избежать коллизий
        // удалить все бумаги, у которых 0 лотов
        stocksToPurchase.removeAll { p ->
            p.lots == 0 || depositManager.portfolioPositions.any { it.ticker == p.stock.ticker }
        }

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
            tickers += "%s*%.2f%%".format(stock.stock.ticker, stock.percentLimitPriceChange)
        }

        return "$price:\n$tickers"
    }

    fun getNotificationTextLong(): String {
        stocksToPurchase.sortBy { abs(it.stock.getPriceDouble() / it.fixedPrice * 100 - 100) }
        stocksToPurchase.sortBy { it.stock.getPriceDouble() / it.fixedPrice * 100 - 100 }
        stocksToPurchase.sortBy { it.status }

        var tickers = ""
        for (stock in stocksToPurchase) {
            val change = (100 * stock.stock.getPriceDouble()) / stock.fixedPrice - 100
            tickers += "${stock.stock.ticker} ${stock.percentLimitPriceChange.toPercent()} = " +
                    "${stock.fixedPrice.toMoney(stock.stock)} ➡ ${stock.stock.getPriceDouble().toMoney(stock.stock)} = " +
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
//        stockManager.resetSubscription(stocksToPurchase.map { it.stock })

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
            // вчерашняя, если стартуем до сессии
            purchase.stock.closePrices?.let {
                purchase.fixedPrice = it.post
            }

            // сегодняшняя, если внутри дня
            purchase.stock.candleToday?.let {
                purchase.fixedPrice = it.closingPrice
            }
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
        stocksToPurchase.sortBy { it.stock.getPriceDouble() / it.fixedPrice * 100.0 - 100.0 }
        for (purchase in stocksToPurchase) {
            val closingPrice = purchase.stock.candleToday?.closingPrice ?: 0.0
            if (closingPrice == 0.0) continue

            val change = closingPrice / purchase.fixedPrice * 100.0 - 100.0
            if (isAllowToBuy(purchase.stock.ticker, change)) {
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
        val sorted = stocksToPurchase.find { it.stock.ticker == ticker }
        sorted?.let { purchase ->
            val change = candle.closingPrice / purchase.fixedPrice * 100.0 - 100.0
            if (change <= purchase.percentLimitPriceChange && isAllowToBuy(ticker, change)) {
                processBuy(purchase, stock, candle)
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

        val change = candle.closingPrice / purchase.fixedPrice * 100.0 - 100.0
        stocksTickerBuyed[stock.ticker] = change
        // просадка < x%
        log("ПРОСАДКА, БЕРЁМ! ${stock.ticker} ➡ $change ➡ ${candle.closingPrice}")

        val baseProfit = SettingsManager.getTazikTakeProfit()
        val job: Job?
        when {
            SettingsManager.getTazikBuyAsk() -> { // покупка из аска
                job = purchase.buyLimitFromAsk(baseProfit)
            }
            SettingsManager.getTazikBuyMarket() -> { // покупка по рынку
                // примерная цена покупки (! средняя будет неизвестна из-за тинька !)
                val priceBuy = purchase.fixedPrice - abs(purchase.fixedPrice / 100.0 * purchase.percentLimitPriceChange)

                // ставим цену продажу относительно примрной средней
                var priceSell = priceBuy + priceBuy / 100.0 * baseProfit

                if (baseProfit == 0.0) priceSell = 0.0
                job = purchase.buyMarket(priceSell)
            }
            else -> { // ставим лимитку
                // ищем цену максимально близкую к просадке
                var delta = abs(change) - abs(purchase.percentLimitPriceChange)

                // 0.80 коэф приближения к нижней точке, в самом низу могут не налить
                delta *= 0.65

                // корректируем % падения для покупки
                val percent = abs(purchase.percentLimitPriceChange) + delta

                // вычислияем финальную цену лимитки
                val buyPrice = purchase.fixedPrice - abs(purchase.fixedPrice / 100.0 * percent)

                // вычисляем процент профита после сдвига лимитки ниже

                // финальный профит
                delta *= 0.65
                var finalProfit = baseProfit + abs(delta)

                if (baseProfit == 0.0) finalProfit = 0.0
                job = purchase.buyLimitFromBid(buyPrice, finalProfit)
            }
        }

        if (job != null) {
            activeJobs.add(job)
        }
    }
}