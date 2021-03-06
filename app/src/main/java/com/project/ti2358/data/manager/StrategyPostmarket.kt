package com.project.ti2358.data.manager

import com.project.ti2358.service.Sorting
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@KoinApiExtension
class StrategyPostmarket : KoinComponent {
    private val stockManager: StockManager by inject()

    var stocks: MutableList<Stock> = mutableListOf()
    var currentSort: Sorting = Sorting.ASCENDING

    fun process(): MutableList<Stock> {
        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        stocks = stockManager.stocksStream.filter { it.getPriceDouble() > min && it.getPriceDouble() < max }.toMutableList()
        stocks.removeAll { it.getPricePostmarketUSDouble() == 0.0 }
        return stocks
    }

    fun resort(): MutableList<Stock> {
        currentSort = if (currentSort == Sorting.DESCENDING) Sorting.ASCENDING else Sorting.DESCENDING
        stocks.sortBy {
            val sign = if (currentSort == Sorting.ASCENDING) 1 else -1
            it.changePricePostmarketPercent * sign
        }
        return stocks
    }
}