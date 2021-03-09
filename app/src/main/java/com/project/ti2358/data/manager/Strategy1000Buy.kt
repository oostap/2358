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
    var stocksToPurchase: MutableList<PurchaseStock> = mutableListOf()
    var currentSort: Sorting = Sorting.DESCENDING

    fun process(): MutableList<Stock> {
        val all = stockManager.stocksStream
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

        stocksToPurchase = stocksSelected.map { stock ->
            PurchaseStock(stock).apply {
                lots = (onePiece / stock.getPriceDouble()).roundToInt()
                status = OrderStatus.WAITING
                percentLimitPriceChange = -1.0 // TODO в настройки
                updateAbsolutePrice()
            }
        }.toMutableList()

        return stocksToPurchase
    }

    fun getTotalPurchaseString(): String {
        var value = 0.0
        for (stock in stocksToPurchase) {
            value += stock.lots * stock.stock.getPriceDouble()
        }
        return "%.1f$".format(value)
    }

    fun getTotalPurchasePieces(): Int {
        var value = 0
        for (stock in stocksToPurchase) {
            value += stock.lots
        }
        return value
    }

    fun getNotificationTextShort(): String {
        val price = getTotalPurchaseString()
        var tickers = ""
        for (stock in stocksToPurchase) {
            tickers += "${stock.lots}*${stock.stock.marketInstrument.ticker} "
        }

        return "$price:\n$tickers"
    }

    fun getNotificationTextLong(): String {
        var tickers = ""
        for (stock in stocksToPurchase) {
            val p = "%.1f$ > %.1f$ > %.1f%%".format(
                stock.lots * stock.getLimitPriceDouble(),
                stock.getLimitPriceDouble(),
                stock.percentLimitPriceChange
            )
            tickers += "${stock.stock.marketInstrument.ticker} * ${stock.lots} = $p ${stock.getStatusString()}\n"
        }

        return tickers
    }
}