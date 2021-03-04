package com.project.ti2358.data.manager

import com.project.ti2358.data.service.SettingsManager
import com.project.ti2358.service.Sorting
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@KoinApiExtension
class StrategyPostmarket : KoinComponent {
    private val stockManager: StockManager by inject()

    var stocks: MutableList<Stock> = mutableListOf()

    fun process(): MutableList<Stock> {
        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        stocks = stockManager.stocksStream.filter { it.getPriceDouble() > min && it.getPriceDouble() < max } as MutableList<Stock>
        return stocks
    }

    fun resort(sort: Sorting = Sorting.ASCENDING): MutableList<Stock> {
        if (sort == Sorting.ASCENDING)
            stocks.sortBy { it.changePricePostmarketPercent }
        else
            stocks.sortByDescending { it.changePricePostmarketPercent }

        return stocks
    }
}