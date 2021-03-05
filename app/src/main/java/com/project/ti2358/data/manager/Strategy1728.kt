package com.project.ti2358.data.manager

import com.project.ti2358.data.manager.PurchaseStock
import com.project.ti2358.data.model.dto.Interval
import com.project.ti2358.data.service.SettingsManager
import com.project.ti2358.service.Sorting
import com.project.ti2358.service.Utils
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

@KoinApiExtension
class Strategy1728() : KoinComponent {
    private val stockManager: StockManager by inject()
    var stocks: MutableList<Stock> = mutableListOf()
    var stocksSelected: MutableList<Stock> = mutableListOf()
    var stocksToPurchase: MutableList<PurchaseStock> = mutableListOf()

    lateinit var strategyStartTime: Calendar
    var currentSort: Sorting = Sorting.DESCENDING

    init {
        resetStrategy()
    }

    fun process(): MutableList<Stock> {
        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()
        val change = SettingsManager.get1728ChangePercent()
        val volumeBeforeStart = SettingsManager.get1728VolumeBeforeStart()
        val volumeAfterStart = SettingsManager.get1728VolumeAfterStart()

        stocks = stockManager.stocksStream.filter { stock ->
            stock.getPriceDouble() > min &&
            stock.getPriceDouble() < max &&
            abs(stock.changePrice1728DayPercent) >= abs(change) &&   // изменение
            stock.getVolume1728AfterStart() >= volumeAfterStart &&
            stock.getVolume1728BeforeStart() >= volumeBeforeStart
        }.toMutableList()

        return stocks
    }

    fun resetStrategy() {
        strategyStartTime = Calendar.getInstance()
        strategyStartTime.set(Calendar.SECOND, 0)

        stockManager.stocksStream.forEach {
            it.reset1728()
        }
    }

    fun resort(): MutableList<Stock> {
        currentSort = if (currentSort == Sorting.DESCENDING) Sorting.ASCENDING else Sorting.DESCENDING
        stocks.sortBy {
            val sign = if (currentSort == Sorting.ASCENDING) 1 else -1
            it.changePrice1728DayPercent * sign
        }
        return stocks
    }

    fun getPurchaseStock(): MutableList<PurchaseStock> {
        val totalMoney: Double = SettingsManager.get2358PurchaseVolume().toDouble()
        val onePiece: Double = totalMoney / stocksSelected.size

        stocksToPurchase = stocksSelected.map {
            PurchaseStock(it).apply {
                lots = (onePiece / stock.getPriceDouble()).roundToInt()
            }
        }.toMutableList()

        return stocksToPurchase
    }
}