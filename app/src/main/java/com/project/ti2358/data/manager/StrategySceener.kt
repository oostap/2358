package com.project.ti2358.data.manager

import com.project.ti2358.data.service.SettingsManager
import com.project.ti2358.service.Sorting
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@KoinApiExtension
class StrategyScreener : KoinComponent {
    private val stockManager: StockManager by inject()

    var stocks: MutableList<Stock> = mutableListOf()

    public fun process() : MutableList<Stock> {
        val all = stockManager.stocksStream

        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        stocks.clear()
        for (stock in all) {
            if (stock.getPriceDouble() > min && stock.getPriceDouble() < max) {
                stocks.add(stock)
            }
        }

        return stocks
    }

    public fun resort(sort : Sorting = Sorting.ASCENDING) : MutableList<Stock> {
        if (sort == Sorting.ASCENDING)
            stocks.sortBy { it.changePriceDayPercent }
        else
            stocks.sortByDescending { it.changePriceDayPercent }

        return stocks
    }
}