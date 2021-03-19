package com.project.ti2358.data.manager

import com.project.ti2358.service.Sorting
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.roundToInt

@KoinApiExtension
class Strategy1000Buy : KoinComponent {
    private val stockManager: StockManager by inject()

    var stocks: MutableList<Stock> = mutableListOf()
    var stocksSelected: MutableList<Stock> = mutableListOf()
    var stocksToBuy: MutableList<PurchaseStock> = mutableListOf()
    var currentSort: Sorting = Sorting.DESCENDING

    var stocksToBuy700: MutableList<PurchaseStock> = mutableListOf()
    var stocksToBuy1000: MutableList<PurchaseStock> = mutableListOf()

    var started700: Boolean = false
    var started1000: Boolean = false

    fun process(): MutableList<Stock> {
        val all = stockManager.getWhiteStocks()
        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        stocks = all.filter { it.getPriceDouble() > min && it.getPriceDouble() < max }.toMutableList()
        stocks.sortBy { it.changePrice2359DayPercent }
        return stocks
    }

    fun resort(): MutableList<Stock> {
        currentSort = if (currentSort == Sorting.DESCENDING) Sorting.ASCENDING else Sorting.DESCENDING
        stocks.sortBy {
            val sign = if (currentSort == Sorting.ASCENDING) 1 else -1
            it.changePrice2359DayPercent * sign
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
        stocksSelected.sortBy { it.changePrice2359DayPercent }
    }

    fun isSelected(stock: Stock): Boolean {
        return stock in stocksSelected
    }

    fun getPurchaseStock(): MutableList<PurchaseStock> {
        val totalMoney: Double = SettingsManager.get1000BuyPurchaseVolume().toDouble()
        val onePiece: Double = totalMoney / stocksSelected.size

        stocksToBuy = stocksSelected.map { stock ->
            PurchaseStock(stock).apply {
                lots = (onePiece / stock.getPriceDouble()).roundToInt()
                status = OrderStatus.WAITING
                percentLimitPriceChange = -1.0 // TODO в настройки
                updateAbsolutePrice()
            }
        }.toMutableList()

        return stocksToBuy
    }

    fun getTotalPurchaseString(stocks: MutableList<PurchaseStock>): String {
        var value = 0.0
        for (stock in stocks) {
            value += stock.lots * stock.stock.getPriceDouble()
        }
        return "%.1f$".format(value)
    }

    fun getTotalPurchasePieces(): Int {
        var value = 0
        for (stock in stocksToBuy) {
            value += stock.lots
        }
        return value
    }

    fun getNotificationTextShort(stocks: MutableList<PurchaseStock>): String {
        val price = getTotalPurchaseString(stocks)
        var tickers = ""
        for (stock in stocks) {
            tickers += "${stock.lots}*${stock.stock.instrument.ticker} "
        }

        return "$price:\n$tickers"
    }

    fun getNotificationTextLong(stocks: MutableList<PurchaseStock>): String {
        var tickers = ""
        for (stock in stocks) {
            val p = "%.1f$ > %.1f$ > %.1f%%".format(
                stock.lots * stock.getLimitPriceDouble(),
                stock.getLimitPriceDouble(),
                stock.percentLimitPriceChange
            )
            tickers += "${stock.stock.instrument.ticker} * ${stock.lots} = $p ${stock.getStatusString()}\n"
        }

        return tickers
    }

    fun prepareBuy700() {
        started700 = false
        stocksToBuy700 = stocksToBuy
    }

    fun prepareBuy1000() {
        started1000 = false
        stocksToBuy1000 = stocksToBuy
    }

    fun startStrategy700Buy() {
        if (started700) return
        started700 = true

        for (purchase in stocksToBuy700) {
            purchase.buyLimitFromBid(purchase.getLimitPriceDouble(), SettingsManager.get1000BuyTakeProfit())
        }
    }

    fun startStrategy1000Buy() {
        if (started1000) return
        started1000 = true

        for (purchase in stocksToBuy1000) {
            purchase.buyLimitFromBid(purchase.getLimitPriceDouble(), SettingsManager.get1000BuyTakeProfit())
        }
    }
}