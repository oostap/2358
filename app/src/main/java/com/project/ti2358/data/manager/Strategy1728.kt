package com.project.ti2358.data.manager

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
    var currentSort: Sorting = Sorting.DESCENDING

    fun process(): MutableList<Stock> {
        val all = stockManager.stocksStream
        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()
        val change = SettingsManager.get1728ChangePercent()
        val volume = SettingsManager.get1728Volume()

        stocks = all.filter { stock ->
                    stock.getPriceDouble() > min &&
                    stock.getPriceDouble() < max &&
                    stock.getTodayVolume() >= volume &&
                    abs(stock.changePriceFixDayPercent) >= abs(change)
        }.toMutableList()

        return stocks
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