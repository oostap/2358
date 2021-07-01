package com.project.ti2358.data.manager

import com.project.ti2358.service.Sorting
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@KoinApiExtension
class StrategyArbitration : KoinComponent {
    private val stockManager: StockManager by inject()

    var stocks: MutableList<Stock> = mutableListOf()
    private var currentSort: Sorting = Sorting.DESCENDING

    var sectorStocks: MutableMap<String, MutableList<Stock>> = mutableMapOf()

    fun process(): MutableList<Stock> {
        val all = stockManager.getWhiteStocks()
        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        stocks = all.filter { it.getPriceNow() > min && it.getPriceNow() < max }.toMutableList()

        val sectors = stockManager.stockSectors
        for (sector in sectors) {
            val s = stocks.filter { it.closePrices?.sector == sector }.toMutableList()
            sectorStocks[sector] = s
        }

        return stocks
    }

    fun resort(sector: String): List<Stock> {
        currentSort = if (currentSort == Sorting.DESCENDING) Sorting.ASCENDING else Sorting.DESCENDING
        val s = sectorStocks[sector]
        s?.sortBy {
            val sign = if (currentSort == Sorting.ASCENDING) 1 else -1
            it.changePrice2300DayPercent * sign
        }
        return s ?: emptyList()
    }
}