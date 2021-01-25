package com.project.ti2358.data.service

import com.project.ti2358.data.manager.PurchaseStock
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.roundToInt

@KoinApiExtension
class Strategy1728() : KoinComponent {
    private val stockManager: StockManager by inject()
    var stocks: MutableList<Stock> = mutableListOf()
    var stocksSelected: MutableList<Stock> = mutableListOf()
    var stocksToPurchase: MutableList<PurchaseStock> = mutableListOf()

    public fun process() : MutableList<Stock> {
        stocks.clear()

        val all = stockManager.stocksStream
        for (stock in all) {
//            if (SettingsManager.isAllowCurrency(stock.marketInstrument.currency)) {
            stocks.add(stock)
//            }
        }

        return stocks
    }

    public fun setSelected(stock: Stock, value : Boolean) {
        if (value) {
            stocksSelected.remove(stock)
        } else {
            if (!stocksSelected.contains(stock))
                stocksSelected.add(stock)
        }
        stocksSelected.sortBy { it.changePriceDayPercent }
    }

    public fun isSelected(stock: Stock) : Boolean {
        return stocksSelected.contains(stock)
    }

    public fun getPurchaseStock() : MutableList<PurchaseStock> {
//        // !!!!! TODO: тестовый тикер !!!!!!
//        if (stocksSelected.isEmpty()) {
//            for (stock in stockManager.stocksStream) {
//                if (stock.marketInstrument.ticker == "RIG") {
//                    stocksSelected.add(stock)
//                }
//            }
//        }

        stocksToPurchase.clear()
        for (stock in stocksSelected) {
            stocksToPurchase.add(PurchaseStock(stock))
        }

        val totalMoney : Double = SettingsManager.get2358PurchaseVolume().toDouble()
        var onePiece : Double = totalMoney / stocksToPurchase.size

        for (stock in stocksToPurchase) {
            stock.lots = (onePiece / stock.stock.todayDayCandle.closingPrice).roundToInt()
        }

        return stocksToPurchase
    }
}