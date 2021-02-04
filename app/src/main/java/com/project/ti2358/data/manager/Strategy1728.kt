package com.project.ti2358.data.service

import com.project.ti2358.data.manager.PurchaseStock
import com.project.ti2358.service.Sorting
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.abs
import kotlin.math.roundToInt

@KoinApiExtension
class Strategy1728() : KoinComponent {
    private val stockManager: StockManager by inject()
    var stocks: MutableList<Stock> = mutableListOf()
    var stocksSelected: MutableList<Stock> = mutableListOf()
    var stocksToPurchase: MutableList<PurchaseStock> = mutableListOf()

    fun process() : MutableList<Stock> {
        stocks.clear()

        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        val change = SettingsManager.get1728ChangePercent()
        val volumeBeforeStart = SettingsManager.get1728VolumeBeforeStart()
        val volumeAfterStart = SettingsManager.get1728VolumeAfterStart()

        val all = stockManager.stocksStream
        for (stock in all) {
            if (stock.getPriceDouble() > min &&
                stock.getPriceDouble() < max &&
                abs(stock.changePrice1728DayPercent) >= abs(change) &&   // изменение
                stock.getVolume1728AfterStart() >= volumeAfterStart &&
                stock.getVolume1728BeforeStart() >= volumeBeforeStart
            ) {
                stocks.add(stock)
            }
        }

        return stocks
    }

    fun resort(sort : Sorting = Sorting.ASCENDING) : MutableList<Stock> {
        if (sort == Sorting.ASCENDING)
            stocks.sortBy { it.changePrice1728DayPercent }
        else
            stocks.sortByDescending { it.changePrice1728DayPercent }

        return stocks
    }

    fun setSelected(stock: Stock, value : Boolean) {
        if (value) {
            stocksSelected.remove(stock)
        } else {
            if (!stocksSelected.contains(stock))
                stocksSelected.add(stock)
        }
        stocksSelected.sortBy { it.changePrice1728DayPercent }
    }

    fun isSelected(stock: Stock) : Boolean {
        return stocksSelected.contains(stock)
    }

    fun getPurchaseStock() : MutableList<PurchaseStock> {
        stocksToPurchase.clear()
        for (stock in stocksSelected) {
            stocksToPurchase.add(PurchaseStock(stock))
        }

        val totalMoney : Double = SettingsManager.get2358PurchaseVolume().toDouble()
        val onePiece : Double = totalMoney / stocksToPurchase.size

        for (stock in stocksToPurchase) {
            stock.lots = (onePiece / stock.stock.getPriceDouble()).roundToInt()
        }

        return stocksToPurchase
    }
}