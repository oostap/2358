package com.project.ti2358.data.manager

import com.project.ti2358.data.model.dto.PortfolioPosition
import com.project.ti2358.service.Sorting
import com.project.ti2358.service.toMoney
import kotlinx.coroutines.Job
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.lang.Exception
import java.util.*
import kotlin.math.abs

@KoinApiExtension
class Strategy1000Sell() : KoinComponent {
    private val depositManager: DepositManager by inject()
    private val stockManager: StockManager by inject()

    var stocks: MutableList<Stock> = mutableListOf()
    var stocksSelected: MutableList<Stock> = mutableListOf()
    private var purchaseToSell: MutableList<PurchaseStock> = mutableListOf()

    var positionsToSell700: MutableList<PurchaseStock> = mutableListOf()
    var positionsToSell1000: MutableList<PurchaseStock> = mutableListOf()
    var currentSort: Sorting = Sorting.DESCENDING

    var job1000: MutableList<Job?> = mutableListOf()
    var job700: MutableList<Job?> = mutableListOf()

    var started700: Boolean = false
    var started1000: Boolean = false

    fun process(): MutableList<Stock> {
        val all = stockManager.getWhiteStocks()
        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        stocks = all.filter { it.getPriceNow() > min && it.getPriceNow() < max }.toMutableList()
        stocks.sortBy { it.changePrice2300DayPercent }

        // удалить все бумаги, по которым нет шорта в ТИ
        stocks.removeAll { it.short == null && depositManager.portfolioPositions.find { p -> p.ticker == it.ticker } == null}
        return stocks
    }

    fun resort(): MutableList<Stock> {
        currentSort = if (currentSort == Sorting.DESCENDING) Sorting.ASCENDING else Sorting.DESCENDING
        stocks.sortBy { stock ->
            val sign = if (currentSort == Sorting.ASCENDING) 1 else -1
            val position = depositManager.portfolioPositions.find { it.ticker == stock.ticker }
            val multiplier1 = if (position != null) (abs(position.lots * position.getAveragePrice())).toInt() else 1
            val multiplier3 = if (stock in stocksSelected) 1000 else 1
            stock.changePrice2300DayPercent * sign - multiplier1 - multiplier3
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

    fun processSellPosition(): MutableList<PurchaseStock> {
        val purchases: MutableList<PurchaseStock> = mutableListOf()

        for (stock in stocksSelected) {
            val purchase = PurchaseStock(stock)
            for (p in purchaseToSell) {
                if (p.ticker == stock.ticker) {
                    purchase.apply {
                        percentProfitSellFrom = p.percentProfitSellFrom
                        lots = p.lots
                    }
                    break
                }
            }

            purchase.apply {
                position = depositManager.getPositionForFigi(stock.figi)
            }
            purchases.add(purchase)
        }
        purchaseToSell = purchases

        purchaseToSell.forEach {
            if (it.percentProfitSellFrom == 0.0) {
                it.processInitialProfit()
            }
            it.status = PurchaseStatus.WAITING
        }
        return purchaseToSell
    }

    fun getTotalPurchasePieces(): Int {
        var value = 0
        for (purchaseStock in purchaseToSell) {
            value += purchaseStock.lots
        }
        return value
    }

    fun getTotalSellString(): String {
        var value = 0.0
        for (purchaseStock in purchaseToSell) {
            value += purchaseStock.getProfitPriceForSell() * purchaseStock.lots
        }
        return value.toMoney(null)
    }

    fun getTotalSellString(purchases: MutableList<PurchaseStock>): String {
        var value = 0.0
        for (purchaseStock in purchases) {
            value += purchaseStock.getProfitPriceForSell() * purchaseStock.lots
        }
        return value.toMoney(null)
    }

    fun getNotificationTextShort(purchases: MutableList<PurchaseStock>): String {
        val price = getTotalSellString(purchases)
        var tickers = ""
        for (purchaseStock in purchases) {
            tickers += "${purchaseStock.lots}*${purchaseStock.ticker} "
        }

        return "$price:\n$tickers"
    }

    fun getNotificationTextLong(purchases: MutableList<PurchaseStock>): String {
        var tickers = ""
        for (purchaseStock in purchases) {
            val p = "%.2f$=%.2f$ > %.1f%%".format(locale = Locale.US, purchaseStock.getProfitPriceForSell(), purchaseStock.lots * purchaseStock.getProfitPriceForSell(), purchaseStock.percentProfitSellFrom)
            tickers += "${purchaseStock.ticker}: ${purchaseStock.lots}* = $p ${purchaseStock.getStatusString()}\n"
        }
        return tickers.trim()
    }

    fun prepareSell700() {
        started700 = false
        positionsToSell700 = purchaseToSell
    }

    fun prepareSell1000() {
        started1000 = false
        positionsToSell1000 = purchaseToSell
    }

    fun startStrategy700Sell() {
        if (started700) return
        started700 = true
        positionsToSell700.forEach {
            job700.add(it.sellMorning())
        }
    }

    fun startStrategy1000Sell() {
        if (started1000) return
        started1000 = true
        positionsToSell1000.forEach {
            job1000.add(it.sellMorning())
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