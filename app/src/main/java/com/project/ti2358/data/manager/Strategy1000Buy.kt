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

    fun process() : MutableList<Stock> {
        val all = stockManager.stocksStream

        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        val change = SettingsManager.get1005ChangePercent()
        var volumeDayPieces = SettingsManager.get1005VolumeDayPieces()

        if (!Utils.isActiveSession()) { // если биржа закрыта, то показать всё
            volumeDayPieces = 0
        }

        stocks.clear()
        for (stock in all) {
            if (stock.getPriceDouble() > min &&
                stock.getPriceDouble() < max &&
                abs(stock.changePrice2359DayPercent) >= abs(change) &&    // изменение
                stock.getTodayVolume() >= volumeDayPieces) {              // объём в шт
                stocks.add(stock)
            }
        }

        return stocks
    }

    fun resort(sort : Sorting = Sorting.ASCENDING) : MutableList<Stock> {
        if (sort == Sorting.ASCENDING)
            stocks.sortBy { it.changePrice2359DayPercent }
        else
            stocks.sortByDescending { it.changePrice2359DayPercent }

        return stocks
    }

    fun setSelected(stock: Stock, value : Boolean) {
        if (value) {
            stocksSelected.remove(stock)
        } else {
            if (!stocksSelected.contains(stock))
                stocksSelected.add(stock)
        }
        stocksSelected.sortBy { it.changePriceDayPercent }
    }

    fun isSelected(stock: Stock) : Boolean {
        return stocksSelected.contains(stock)
    }

    fun getPurchaseStock() : MutableList<PurchaseStock> {
        stocksToPurchase.clear()
        for (stock in stocksSelected) {
            stocksToPurchase.add(PurchaseStock(stock))
        }

        val totalMoney : Double = SettingsManager.get1000BuyPurchaseVolume().toDouble()
        val onePiece : Double = totalMoney / stocksToPurchase.size

        for (purchase in stocksToPurchase) {
            purchase.lots = (onePiece / purchase.stock.getPriceDouble()).roundToInt()
            purchase.status = PurchaseStatus.WAITING
        }

        return stocksToPurchase
    }

    fun getTotalPurchaseString() : String {
        var value = 0.0
        for (stock in stocksToPurchase) {
            value += stock.lots * stock.stock.getPriceDouble()
        }
        return "%.1f$".format(value)
    }

    fun getTotalPurchasePieces() : Int {
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
            val p = "%.1f$".format(stock.lots * stock.stock.getPriceDouble())
            tickers += "${stock.stock.marketInstrument.ticker} * ${stock.lots} шт. = ${p} ${stock.getStatusString()}\n"
        }

        return tickers
    }
}