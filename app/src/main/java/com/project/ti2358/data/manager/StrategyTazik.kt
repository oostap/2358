package com.project.ti2358.data.manager

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.project.ti2358.TheApplication
import com.project.ti2358.service.*
import kotlinx.coroutines.*
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

@KoinApiExtension
class StrategyTazik : KoinComponent {
    private val stockManager: StockManager by inject()
    private val depositManager: DepositManager by inject()

    val keySavedSelectedStock: String = "tazik_selected"

    var stocks: MutableList<Stock> = mutableListOf()
    var stocksSelected: MutableList<Stock> = mutableListOf()

    var stocksToPurchase: MutableList<PurchaseStock> = mutableListOf()
    var stocksBuyed: MutableList<Stock> = mutableListOf()

    var started: Boolean = false

    var scheduledStartTime: Calendar? = null
    var job: Job? = null

    var currentSort: Sorting = Sorting.DESCENDING
    private val gson = Gson()

    fun process(numberSet: Int): MutableList<Stock> {
        val all = stockManager.stocksStream
        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        stocks = all.filter { it.getPriceDouble() > min && it.getPriceDouble() < max }.toMutableList()
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
            stocksSelected = stocks.filter { it.marketInstrument.ticker in stocksSelectedList }.toMutableList()
        }
    }

    private fun saveSelectedStocks(numberSet: Int) {
        val list = stocksSelected.map { it.marketInstrument.ticker }.toMutableList()

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
            p.lots == 0 || depositManager.portfolioPositions.any { it.ticker == p.stock.marketInstrument.ticker }
        }

        return stocksToPurchase
    }

    fun getNotificationTitle(): String {
        if (started) return "Внимание! Работает автотазик!"

        return if (scheduledStartTime == null) {
            "Старт тазика через ???"
        } else {
            val now = Calendar.getInstance(TimeZone.getDefault())
            val current = scheduledStartTime?.timeInMillis ?: 0
            val scheduleDelay = current - now.timeInMillis

            val allSeconds = scheduleDelay / 1000
            val hours = allSeconds / 3600
            val minutes = (allSeconds - hours * 3600) / 60
            val seconds = allSeconds % 60

            "Старт тазика через %02d:%02d:%02d".format(hours, minutes, seconds)
        }
    }

    fun getTotalPurchaseString(): String {
        val volume = SettingsManager.getTazikPurchaseVolume().toDouble()
        val p = SettingsManager.getTazikPurchaseParts()
        return String.format("%d по %.2f$", p, volume / p)
    }

    fun getNotificationTextShort(): String {
        val price = getTotalPurchaseString()
        var tickers = ""
        for (stock in stocksToPurchase) {
            tickers += "${stock.stock.marketInstrument.ticker}*${stock.percentLimitPriceChange}%"
        }

        return "$price:\n$tickers"
    }

    fun getNotificationTextLong(): String {
        stocksToPurchase.sortBy { (100 * it.stock.priceNow) / it.stock.priceTazik - 100 }
        stocksToPurchase.sortBy { it.status }

        var tickers = ""
        for (stock in stocksToPurchase) {
            val change = (100 * stock.stock.priceNow) / stock.stock.priceTazik - 100
            tickers += "${stock.stock.marketInstrument.ticker} ${stock.percentLimitPriceChange.toPercent()} = " +
                    "${stock.stock.priceTazik.toDollar()} ➡ ${stock.stock.priceNow.toDollar()} = " +
                    "${change.toPercent()} ${stock.getStatusString()}\n"

        }

        return tickers
    }

    fun prepareStrategy(scheduled : Boolean, time: String) {
        if (!scheduled) {
            startStrategy()
            return
        }
        started = false

        val differenceHours: Int = Utils.getTimeDiffBetweenMSK()
        val dayTime = time.split(":").toTypedArray()
        if (dayTime.size < 3) {
            Utils.showToastAlert("Неверный формат времени $time")
            return
        }

        // запустить таймер
        job?.cancel()
        job = GlobalScope.launch(Dispatchers.Main) {
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
                    Utils.showToastAlert("Ошибка! Отрицательное время!? втф = $time")
                    return@launch
                }

                delay(scheduleDelay)
                startStrategy()
            }
        }

        GlobalScope.launch(Dispatchers.Main) {
            while (!started) {
                fixPrice()
                delay(1 * 1000 * 2)
            }
        }
    }

    private fun fixPrice() {
        // зафикировать цену, чтобы change считать от неё
        for (stock in stocks) {
            // вчерашняя, если стартуем до сессии
            stock.candleWeek?.let {
                stock.priceTazik = it.closingPrice
            }

            // вчерашняя, если стартуем до сессии
            stock.candleYesterday?.let {
                stock.priceTazik = it.closingPrice
            }

            // сегодняшняя, если внутри дня
            stock.candle1000?.let {
                stock.priceTazik = it.closingPrice
            }
        }
    }

    fun startStrategy() {
        started = true
        stocksBuyed.clear()

        fixPrice()
    }

    fun stopStrategy() {
        job?.cancel()
        started = false
        stocksBuyed.clear()
    }

    fun processStrategy(stock: Stock) {
        if (!started) return

        // если бумага не в списке скана - игнорируем
        val sorted = stocksToPurchase.filter { it.stock == stock }
        if (sorted.isEmpty()) return

        val purchase = sorted.first()
        stock.candle1000?.let {
            val change = (100 * it.closingPrice) / stock.priceTazik - 100
            if (change >= purchase.percentLimitPriceChange) return

            // просадка < -1%
            log("ПРОСАДКА: ${stock.marketInstrument} ➡ $change ➡ ${it.closingPrice}")

            // уже брали бумагу?
            if (stocksBuyed.contains(stock)) return

            log("ПРОСАДКА: ПОКУПАЕМ!! ${stock.marketInstrument}")
            when {
                SettingsManager.getTazikBuyAsk() -> { // покупка из аска
                    purchase.buyLimitFromAsk(SettingsManager.getTazikTakeProfit())
                }
                SettingsManager.getTazikBuyMarket() -> { // покупка по рынку
                    // примерна цена покупки (! средняя будет неизвестна из-за тинька !)
                    val priceBuy = stock.priceTazik - abs(stock.priceTazik / 100.0 * purchase.percentLimitPriceChange)

                    // ставим цену продажу относительно примрной средней
                    val priceSell = priceBuy + priceBuy / 100.0 * SettingsManager.getTazikTakeProfit()

                    purchase.buyMarket(priceSell)
                }
                else -> { // ставим лимитку
                    // ищем цену максимально близкую к просадке
                    var delta = abs(change) - abs(purchase.percentLimitPriceChange)

                    // 0.80 коэф приближения к нижней точке, в самом низу могут не налить
                    delta *= 0.70

                    // корректируем % падения для покупки
                    val percent = abs(purchase.percentLimitPriceChange) + delta

                    // вычислияем финальную цену лимитки
                    val buyPrice = stock.priceTazik - abs(stock.priceTazik / 100.0 * percent)

                    // вычисляем процент профита после сдвига лимитки ниже
                    val baseProfit = SettingsManager.getTazikTakeProfit()

                    // финальный профит
                    delta *= 0.70
                    val finalProfit = baseProfit + abs(delta)
                    purchase.buyLimitFromBid(buyPrice, finalProfit)
                }
            }

            // завершение стратегии
            val parts = SettingsManager.getTazikPurchaseParts()
            stocksBuyed.add(stock)
            if (stocksBuyed.size >= parts) {
                stopStrategy()
            }
        }
    }
}