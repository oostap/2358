package com.project.ti2358.data.manager

import com.project.ti2358.data.service.SettingsManager
import com.project.ti2358.service.toPercent
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.roundToInt

@KoinApiExtension
class Strategy2358() : KoinComponent {
    private val stockManager: StockManager by inject()

    var stocks: MutableList<Stock> = mutableListOf()
    var stocksSelected: MutableList<Stock> = mutableListOf()
    var stocksToPurchase: MutableList<PurchaseStock> = mutableListOf()

    fun process(): MutableList<Stock> {
        val change = SettingsManager.get2358ChangePercent()
        val volumeDayPieces = SettingsManager.get2358VolumeDayPieces()
        val volumeDayCash = SettingsManager.get2358VolumeDayCash() * 1000 * 1000
        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        stocks = stockManager.stocksStream.filter { stock ->
            stock.changePrice2359DayPercent <= change &&    // изменение
            stock.getTodayVolume() >= volumeDayPieces &&    // объём в шт
            stock.dayVolumeCash >= volumeDayCash &&         // объём в $
            stock.getPriceDouble() > min &&                 // мин цена
            stock.getPriceDouble() < max                    // макс цена
        }.toMutableList()

        stocks.sortBy {
            val multiplier = if (stocksSelected.contains(it)) 100 else 1
            it.changePrice2359DayPercent * multiplier
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
        return stocksSelected.contains(stock)
    }

    fun getPurchaseStock(reset: Boolean): MutableList<PurchaseStock> {
        process()

        // удалить бумаги, которые перестали удовлетворять условию 2358
        stocksSelected.removeAll { !stocks.contains(it) }

        // проверить и удалить бумаги, которые сильно отросли с момента старта таймера
        if (!reset) {
            val stocksToDelete: MutableList<Stock> = mutableListOf()
            for (stock in stocksSelected) {
                if (stock.changeOnStartTimer == 0.0) continue

                val delta = stock.changeOnStartTimer / stock.changePrice2359DayPercent

                // если бумага отросла больше, чем на половину, то отменить покупку
                if (delta >= 1.6) {
                    stocksToDelete.add(stock)
                }
            }

            stocksSelected.removeAll { stocksToDelete.contains(it) }
        }

        val purchases: MutableList<PurchaseStock> = mutableListOf()
        for (stock in stocksSelected) {
            val purchase = PurchaseStock(stock)

            var exists = false
            for (p in stocksToPurchase) {
                if (p.stock.marketInstrument.ticker == stock.marketInstrument.ticker) {
                    purchase.percentProfitSellFrom = p.percentProfitSellFrom
                    purchase.percentProfitSellTo = p.percentProfitSellTo
                    exists = true
                    break
                }
            }

            if (!exists) {
                purchase.percentProfitSellFrom = SettingsManager.get2358TakeProfitFrom()
                purchase.percentProfitSellTo = SettingsManager.get2358TakeProfitTo()
            }

            purchases.add(purchase)
        }
        stocksToPurchase = purchases

        val totalMoney: Double = SettingsManager.get2358PurchaseVolume().toDouble()
        val onePiece: Double = totalMoney / stocksToPurchase.size

        for (purchase in stocksToPurchase) {
            purchase.lots = (onePiece / purchase.stock.getPriceDouble()).roundToInt()
            purchase.status = OrderStatus.WAITING

            if (reset) { // запоминаем % подготовки, чтобы после проверить изменение
                purchase.stock.changeOnStartTimer = purchase.stock.changePrice2359DayPercent
                purchase.percentProfitSellFrom = SettingsManager.get2358TakeProfitFrom()
                purchase.percentProfitSellTo = SettingsManager.get2358TakeProfitTo()
            }
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
            val p = "%.1f$".format(stock.lots * stock.stock.getPriceDouble())
            tickers += "${stock.stock.marketInstrument.ticker}*${stock.lots} = ${p}, ${stock.percentProfitSellFrom.toPercent()}-${stock.percentProfitSellTo.toPercent()}, ${stock.getStatusString()}\n"
        }

        return tickers
    }
}