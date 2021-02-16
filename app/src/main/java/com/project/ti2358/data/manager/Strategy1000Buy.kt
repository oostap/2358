package com.project.ti2358.data.manager

import com.project.ti2358.data.service.SettingsManager
import com.project.ti2358.service.Sorting
import com.project.ti2358.service.Utils
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.abs
import kotlin.math.roundToInt

@KoinApiExtension
class Strategy1000Buy : KoinComponent {
    private val stockManager: StockManager by inject()

    var stocks: MutableList<Stock> = mutableListOf()
    var stocksSelected: MutableList<Stock> = mutableListOf()
    var stocksToPurchase: MutableList<PurchaseStock> = mutableListOf()

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
        return stocks
    }

    fun resort(sort: Sorting = Sorting.ASCENDING): MutableList<Stock> {
        if (sort == Sorting.ASCENDING)
            stocks.sortBy { it.changePrice2359DayPercent }
        else
            stocks.sortByDescending { it.changePrice2359DayPercent }

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
    }

    fun isSelected(stock: Stock): Boolean {
        return stocksSelected.contains(stock)
    }

    fun getPurchaseStock(): MutableList<PurchaseStock> {
        stocksToPurchase.clear()
        val totalMoney: Double = SettingsManager.get1000BuyPurchaseVolume().toDouble()
        val onePiece: Double = totalMoney / stocksSelected.size

        for (stock in stocksSelected) {
            val purchase = PurchaseStock(stock)
            purchase.lots = (onePiece / purchase.stock.getPriceDouble()).roundToInt()
            purchase.status = PurchaseStatus.WAITING
            purchase.percentLimitPriceChange = -1.0 // TODO в настройки
            purchase.updateAbsolutePrice()
            stocksToPurchase.add(purchase)
        }

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
            val p = "%.1f$ -> %.1f$ -> %.1f".format(
                stock.lots * stock.getLimitPriceDouble(),
                stock.getLimitPriceDouble(),
                stock.percentLimitPriceChange
            ) + "%"
            tickers += "${stock.stock.marketInstrument.ticker} * ${stock.lots} шт. = ${p} ${stock.getStatusString()}\n"
        }

        return tickers
    }
}