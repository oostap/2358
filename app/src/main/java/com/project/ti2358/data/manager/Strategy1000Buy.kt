package com.project.ti2358.data.manager

import com.project.ti2358.service.Sorting
import kotlinx.coroutines.Job
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.lang.Exception
import java.util.*
import kotlin.math.roundToInt

@KoinApiExtension
class Strategy1000Buy : KoinComponent {
    private val stockManager: StockManager by inject()

    var stocks: MutableList<Stock> = mutableListOf()
    var stocksSelected: MutableList<Stock> = mutableListOf()
    var purchaseToBuy: MutableList<PurchaseStock> = mutableListOf()
    var currentSort: Sorting = Sorting.DESCENDING

    var stocksToBuy700: MutableList<PurchaseStock> = mutableListOf()
    var stocksToBuy1000: MutableList<PurchaseStock> = mutableListOf()

    var started700: Boolean = false
    var started1000: Boolean = false

    var job1000: MutableList<Job?> = mutableListOf()
    var job700: MutableList<Job?> = mutableListOf()

    fun process(): MutableList<Stock> {
        val all = stockManager.getWhiteStocks()
        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        stocks = all.filter { it.getPriceNow() > min && it.getPriceNow() < max }.toMutableList()
        stocks.sortBy { it.changePrice2300DayPercent }
        return stocks
    }

    fun resort(): MutableList<Stock> {
        currentSort = if (currentSort == Sorting.DESCENDING) Sorting.ASCENDING else Sorting.DESCENDING
        stocks.sortBy {
            val sign = if (currentSort == Sorting.ASCENDING) 1 else -1
            it.changePrice2300DayPercent * sign
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
    }

    fun isSelected(stock: Stock): Boolean {
        return stock in stocksSelected
    }

    fun getPurchaseStock(): MutableList<PurchaseStock> {
        val totalMoney: Double = SettingsManager.get1000BuyPurchaseVolume().toDouble()
        val onePiece: Double = totalMoney / stocksSelected.size

        val purchases: MutableList<PurchaseStock> = mutableListOf()
        for (stock in stocksSelected) {
            val purchase = PurchaseStock(stock)
            for (p in purchaseToBuy) {
                if (p.ticker == stock.ticker) {
                    purchase.apply {
                        percentLimitPriceChange = p.percentLimitPriceChange
                        lots = p.lots
                    }
                    break
                }
            }
            purchases.add(purchase)
        }
        purchaseToBuy = purchases

        purchaseToBuy.forEach {
            if (it.percentLimitPriceChange == 0.0) {
                it.percentLimitPriceChange = -1.0
            }
            it.updateAbsolutePrice()
            if (it.lots == 0) { // если уже настраивали количество, то не трогаем
                it.lots = (onePiece / it.getLimitPriceDouble()).roundToInt()
            }
            it.status = PurchaseStatus.WAITING
        }

        return purchaseToBuy
    }

    fun getTotalPurchaseString(purchases: MutableList<PurchaseStock>): String {
        var value = 0.0
        for (p in purchases) {
            value += p.lots * p.getLimitPriceDouble()
        }
        return "%.1f$".format(locale = Locale.US, value)
    }

    fun getTotalPurchasePieces(): Int {
        var value = 0
        for (stock in purchaseToBuy) {
            value += stock.lots
        }
        return value
    }

    fun getNotificationTextShort(purchases: MutableList<PurchaseStock>): String {
        val price = getTotalPurchaseString(purchases)
        var tickers = ""
        for (p in purchases) {
            tickers += "${p.lots}*${p.ticker} "
        }

        return "$price:\n$tickers"
    }

    fun getNotificationTextLong(purchases: MutableList<PurchaseStock>): String {
        var tickers = ""
        for (p in purchases) {
            val text = "%.1f$ > %.2f$ > %.2f%%".format(locale = Locale.US,
                p.lots * p.getLimitPriceDouble(),
                p.getLimitPriceDouble(),
                p.percentLimitPriceChange
            )
            tickers += "${p.ticker} * ${p.lots} = $text ${p.getStatusString()}\n"
        }

        return tickers
    }

    fun prepareBuy700() {
        started700 = false
        stocksToBuy700 = purchaseToBuy
    }

    fun prepareBuy1000() {
        started1000 = false
        stocksToBuy1000 = purchaseToBuy
    }

    fun startStrategy700Buy() {
        if (started700) return
        started700 = true

        for (purchase in stocksToBuy700) {
            job700.add(purchase.buyLimitFromBid(purchase.getLimitPriceDouble(), SettingsManager.get1000BuyTakeProfit()))
        }
    }

    fun startStrategy1000Buy() {
        if (started1000) return
        started1000 = true

        for (purchase in stocksToBuy1000) {
            job1000.add(purchase.buyLimitFromBid(purchase.getLimitPriceDouble(), SettingsManager.get1000BuyTakeProfit()))
        }
    }

    fun stopStrategy700() {
        job700.forEach {
            try {
                if (it?.isActive == true) {
                    it.cancel()
                }
            } catch (e: Exception) {

            }
        }
        job700.clear()
    }

    fun stopStrategy1000() {
        job1000.forEach {
            try {
                if (it?.isActive == true) {
                    it.cancel()
                }
            } catch (e: Exception) {

            }
        }
        job1000.clear()
    }
}