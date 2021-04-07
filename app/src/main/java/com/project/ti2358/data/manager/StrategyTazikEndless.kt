package com.project.ti2358.data.manager

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.project.ti2358.R
import com.project.ti2358.TheApplication
import com.project.ti2358.data.model.dto.Candle
import com.project.ti2358.service.*
import kotlinx.coroutines.*
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.lang.Exception
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

@KoinApiExtension
class StrategyTazikEndless : KoinComponent {
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

    var jobResetPrice: Job? = null

    var currentSort: Sorting = Sorting.DESCENDING

    companion object {
        const val PercentLimitChangeDelta = 0.05
    }

    fun process(): MutableList<Stock> {
        val all = stockManager.getWhiteStocks()
        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        stocks = all.filter { (it.getPriceNow() > min && it.getPriceNow() < max) || it.getPriceNow() == 0.0 }.toMutableList()
        stocks.sortBy { it.changePrice2300DayPercent }
        loadSelectedStocks()
        return stocks
    }

    private fun loadSelectedStocks() {
        stocksSelected.clear()

        val stocksSelectedList: List<String> = SettingsManager.getTazikEndlessSet()
        stocksSelected = stocks.filter { it.ticker in stocksSelectedList }.toMutableList()
    }

    private fun saveSelectedStocks() {
        val setList = stocksSelected.map { it.ticker }.toMutableList()

        val preferences = PreferenceManager.getDefaultSharedPreferences(TheApplication.application.applicationContext)
        val editor: SharedPreferences.Editor = preferences.edit()
        val key = TheApplication.application.applicationContext.getString(R.string.setting_key_tazik_endless_set)
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

    fun setSelected(stock: Stock, value: Boolean) {
        if (value) {
            if (stock !in stocksSelected)
                stocksSelected.add(stock)
        } else {
            stocksSelected.remove(stock)
        }
        stocksSelected.sortBy { it.changePrice2300DayPercent }

        saveSelectedStocks()
    }

    fun isSelected(stock: Stock): Boolean {
        return stock in stocksSelected
    }

    fun getPurchaseStock(): MutableList<PurchaseStock> {
        stocksToPurchase.clear()

        val percent = SettingsManager.getTazikEndlessChangePercent()
        val totalMoney: Double = SettingsManager.getTazikEndlessPurchaseVolume().toDouble()
        val onePiece: Double = totalMoney / SettingsManager.getTazikEndlessPurchaseParts()

        stocksToPurchase = stocksSelected.map {
            PurchaseStock(it).apply {
                percentLimitPriceChange = percent
                lots = (onePiece / stock.getPriceNow()).roundToInt()
                updateAbsolutePrice()
                status = PurchaseStatus.WAITING
            }
        }.toMutableList()

        // удалить все бумаги, которые уже есть в портфеле, чтобы избежать коллизий
        // удалить все бумаги, у которых 0 лотов = не хватает на покупку одной части
        stocksToPurchase.removeAll { p ->
            p.lots == 0 || depositManager.portfolioPositions.any { it.ticker == p.ticker }
        }

        stocksToPurchaseClone = stocksToPurchase.toMutableList()

        return stocksToPurchase
    }

    fun getNotificationTitle(): String {
        if (started) return "Внимание! Работает бесконечный таз!"

        return "Бесконечный таз приостановлен"
    }

    @Synchronized
    fun getTotalPurchaseString(): String {
        val volume = SettingsManager.getTazikEndlessPurchaseVolume().toDouble()
        val p = SettingsManager.getTazikEndlessPurchaseParts()
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
        stocksToPurchase.sortBy { abs(it.stock.getPriceNow() / it.tazikEndlessPrice * 100 - 100) }
        stocksToPurchase.sortBy { it.stock.getPriceNow() / it.tazikEndlessPrice * 100 - 100 }
        stocksToPurchase.sortBy { it.status }

        var tickers = ""
        for (stock in stocksToPurchase) {
            val change = (100 * stock.stock.getPriceNow()) / stock.tazikEndlessPrice - 100
            tickers += "${stock.ticker} ${stock.percentLimitPriceChange.toPercent()} = " +
                    "${stock.tazikEndlessPrice.toMoney(stock.stock)} ➡ ${stock.stock.getPriceNow().toMoney(stock.stock)} = " +
                    "${change.toPercent()} ${stock.getStatusString()}\n"
        }

        return tickers
    }

    private fun fixPrice() {
        // зафикировать цену, чтобы change считать от неё
        for (purchase in stocksToPurchaseClone) {
            purchase.tazikEndlessPrice = purchase.stock.getPriceNow()
        }
    }

    @Synchronized
    fun startStrategy() {
        basicPercentLimitPriceChange = SettingsManager.getTazikEndlessChangePercent()

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

        jobResetPrice?.cancel()
        jobResetPrice = GlobalScope.launch(Dispatchers.Main) {
            while (true) {
                val seconds = SettingsManager.getTazikEndlessResetIntervalSeconds().toLong()
                delay(1000 * seconds)
                fixPrice()
            }
        }
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
        jobResetPrice?.cancel()
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
        val parts = SettingsManager.getTazikEndlessPurchaseParts()
        if (activeJobs.size >= parts) return false

        // ещё не брали бумагу?
        if (ticker !in stocksTickerBuyed) {
            log("TAZIK 1: $ticker $stocksTickerBuyed ${stocksTickerBuyed[ticker]} $change")
            return true
        }

        return false
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
                val change = candle.closingPrice / purchase.tazikEndlessPrice * 100.0 - 100.0
                if (purchase.tazikEndlessPrice != 0.0 &&
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
        val parts = SettingsManager.getTazikEndlessPurchaseParts()
        if (activeJobs.size >= parts) { // TODO: не останавливать стратегию автоматически
            stopStrategy()
            return
        }

        if (purchase.tazikEndlessPrice == 0.0) {
            purchase.tazikEndlessPrice = candle.closingPrice
            return
        }

        val change = candle.closingPrice / purchase.tazikEndlessPrice * 100.0 - 100.0
        stocksTickerBuyed[stock.ticker] = change
        // просадка < x%
        log("ПРОСАДКА, ТАРИМ! ${stock.ticker} ➡ $change ➡ ${candle.closingPrice}")

        val baseProfit = SettingsManager.getTazikEndlessTakeProfit()

        // ищем цену максимально близкую к просадке
        var delta = abs(change) - abs(purchase.percentLimitPriceChange)

        // 0.80 коэф приближения к нижней точке, в самом низу могут не налить
        delta *= SettingsManager.getTazikEndlessApproximationFactor()

        // корректируем % падения для покупки
        val percent = abs(purchase.percentLimitPriceChange) + delta

        // вычислияем финальную цену лимитки
        val buyPrice = purchase.tazikEndlessPrice - abs(purchase.tazikEndlessPrice / 100.0 * percent)

        // вычисляем процент профита после сдвига лимитки ниже

        // финальный профит
        delta *= SettingsManager.getTazikEndlessApproximationFactor()
        var finalProfit = baseProfit + abs(delta)

        if (baseProfit == 0.0) finalProfit = 0.0
        val job = purchase.buyLimitFromBid(buyPrice, finalProfit, 1, SettingsManager.getTazikEndlessOrderLifeTimeSeconds())

        if (job != null) {
            activeJobs.add(job)
        }
    }
}