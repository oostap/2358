package com.project.ti2358.data.manager

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.project.ti2358.data.model.dto.Candle
import com.project.ti2358.data.model.dto.MarketInstrument
import com.project.ti2358.data.service.SettingsManager
import com.project.ti2358.service.Sorting
import com.project.ti2358.service.log
import com.project.ti2358.service.toDollar
import com.project.ti2358.service.toPercent
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
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

    private val gson = Gson()

    fun process(): MutableList<Stock> {
        val all = stockManager.stocksStream

        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        stocks.clear()
        for (stock in all) {
            if (stock.getPriceDouble() > min &&
                stock.getPriceDouble() < max
            ) {
                stocks.add(stock)
            }
        }
        stocks.sortBy { it.changePrice2359DayPercent }
        loadSavedSelectedStocks()
        return stocks
    }

    private fun loadSavedSelectedStocks() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(SettingsManager.context)
        val jsonStocks = preferences.getString(keySavedSelectedStock, null)
        if (jsonStocks != null) {
            val itemType = object : TypeToken<List<String>>() {}.type
            val stocksSelectedList: MutableList<String> = gson.fromJson(jsonStocks, itemType)

            stocksSelected.clear()
            for (stock in stocks) {
                if (stocksSelectedList.contains(stock.marketInstrument.ticker)) {
                    stocksSelected.add(stock)
                }
            }
        }

        stocks.sortBy { stocksSelected.contains(it) }
    }

    fun resort(sort: Sorting = Sorting.ASCENDING): MutableList<Stock> {
        if (sort == Sorting.ASCENDING)
            stocks.sortBy { it.changePrice2359DayPercent }
        else
            stocks.sortByDescending { it.changePrice2359DayPercent }

        stocks.sortByDescending { stocksSelected.contains(it) }
        return stocks
    }

    fun setSelected(stock: Stock, value: Boolean) {
        if (value) {
            stocksSelected.remove(stock)
        } else {
            if (!stocksSelected.contains(stock))
                stocksSelected.add(stock)
        }
        stocksSelected.sortBy { it.changePrice2359DayPercent }

        // сохранить выбранные тикеры
        val list: MutableList<String> = mutableListOf()
        for (s in stocksSelected) {
            list.add(s.marketInstrument.ticker)
        }

        val preferences = PreferenceManager.getDefaultSharedPreferences(SettingsManager.context)
        val editor: SharedPreferences.Editor = preferences.edit()
        val data = gson.toJson(list)
        editor.putString(keySavedSelectedStock, data)
        editor.apply()
    }

    fun isSelected(stock: Stock): Boolean {
        return stocksSelected.contains(stock)
    }

    fun getPurchaseStock(): MutableList<PurchaseStock> {
        stocksToPurchase.clear()

        val percent = SettingsManager.getTazikChangePercent()
        val totalMoney: Double = SettingsManager.getTazikPurchaseVolume().toDouble()
        val onePiece: Double = totalMoney / SettingsManager.getTazikPurchaseParts()

        for (stock in stocksSelected) {
            val purchase = PurchaseStock(stock)
            purchase.percentLimitPriceChange = percent
            purchase.lots = (onePiece / purchase.stock.getPriceDouble()).roundToInt()
            purchase.updateAbsolutePrice()
            purchase.status = PurchaseStatus.WAITING
            stocksToPurchase.add(purchase)
        }

        // удалить все бумаги, которые уже есть в портфеле, чтобы избежать коллизий
        stocksToPurchase.removeAll { p ->
            depositManager.portfolioPositions.any { it.ticker == p.stock.marketInstrument.ticker }
        }

        // удалить все бумаги, у которых 0 лотов
        stocksToPurchase.removeAll { it.lots == 0 }

        return stocksToPurchase
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
            tickers += "${stock.stock.marketInstrument.ticker}*${stock.percentLimitPriceChange}%%"
        }

        return "$price:\n$tickers"
    }

    fun getNotificationTextLong(): String {
        stocksToPurchase.sortBy {
            (100 * it.stock.priceNow) / it.stock.priceTazik - 100
        }

        stocksToPurchase.sortBy {
            it.status
        }

        var tickers = ""
        for (stock in stocksToPurchase) {
            val change = (100 * stock.stock.priceNow) / stock.stock.priceTazik - 100
            tickers += "${stock.stock.marketInstrument.ticker} ${stock.percentLimitPriceChange.toPercent()} = " +
                    "${stock.stock.priceTazik.toDollar()} -> ${stock.stock.priceNow.toDollar()} = " +
                    "${change.toPercent()} ${stock.getStatusString()}\n"

        }

        return tickers
    }

    fun startStrategy() {
        started = true
        stocksBuyed.clear()

        // зафикировать цену, чтобы change считать от неё
        for (stock in stocks) {
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

    fun stopStrategy() {
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
            if (change < purchase.percentLimitPriceChange) { // просадка -1%
                log("ПРОСАДКА: ${stock.marketInstrument} -> $change -> ${it.closingPrice}")

                // лимитная покупка по этой цене
                if (!stocksBuyed.contains(stock)) {
                    log("ПРОСАДКА: ПОКУПАЕМ!! ${stock.marketInstrument}")

                    when {
                        SettingsManager.getTazikBuyAsk() -> { // покупка из аска
                            purchase.buyLimitFromAsk(SettingsManager.getTazikTakeProfit())
                        }
                        SettingsManager.getTazikBuyMarket() -> { // покупка по маркету
                            // примерна цена покупки (! средняя будет неизвестна из-за тинька !)
                            val priceBuy = stock.priceTazik - stock.priceTazik / 100.0 * purchase.percentLimitPriceChange

                            // ставим цену продажу относительно примрной средней
                            var priceSell = priceBuy + priceBuy / 100.0 * SettingsManager.getTazikTakeProfit()
                            // привести цену к норм виду
                            priceSell = (priceSell * 100.0).roundToInt() / 100.0

                            purchase.buyMarket(priceSell)
                        }
                        else -> { // ставим лимитку на наш %
                            var buyPrice = stock.priceTazik - stock.priceTazik / 100.0 * purchase.percentLimitPriceChange
                            buyPrice = (buyPrice * 100.0).roundToInt() / 100.0
                            purchase.buyLimitFromBid(buyPrice, SettingsManager.getTazikTakeProfit())
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
    }
}