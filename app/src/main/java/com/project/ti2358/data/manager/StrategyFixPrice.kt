package com.project.ti2358.data.manager

import com.project.ti2358.service.Sorting
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

@KoinApiExtension
class StrategyFixPrice() : KoinComponent {
    private val stockManager: StockManager by inject()
    var stocks: MutableList<Stock> = mutableListOf()
    var stocksSelected: MutableList<Stock> = mutableListOf()
    var stocksToPurchase: MutableList<PurchaseStock> = mutableListOf()

    var currentSort: Sorting = Sorting.DESCENDING

    init {
        resetStrategy()
    }

    companion object {
        var strategyStartTime: Calendar = Calendar.getInstance()
    }

    fun process(): MutableList<Stock> {
        val all = stockManager.stocksStream
        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()
        stocks = all.filter { stock -> stock.getPriceDouble() > min && stock.getPriceDouble() < max }.toMutableList()
        return stocks
    }

    fun resetStrategy() {
        strategyStartTime = Calendar.getInstance()
        strategyStartTime.set(Calendar.SECOND, 0)

        stockManager.stocksStream.forEach {
            it.resetFixPrice()
        }
    }

    fun resort(): MutableList<Stock> {
        currentSort = if (currentSort == Sorting.DESCENDING) Sorting.ASCENDING else Sorting.DESCENDING
        stocks.sortBy {
            val sign = if (currentSort == Sorting.ASCENDING) 1 else -1
            it.changePriceFixDayPercent * sign
        }
        return stocks
    }
}