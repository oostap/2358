package com.project.ti2358.data.manager

import com.project.ti2358.service.Sorting
import com.project.ti2358.service.Utils
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.abs

@KoinApiExtension
class StrategyPremarket : KoinComponent {
    private val stockManager: StockManager by inject()

    var stocks: MutableList<Stock> = mutableListOf()
    private var currentSort: Sorting = Sorting.DESCENDING

    fun process(): MutableList<Stock> {
        val all = stockManager.getWhiteStocks()
        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()
        val change = SettingsManager.getPremarketChangePercent()
        var volumeMin = SettingsManager.getPremarketVolumeMin()
        var volumeMax = SettingsManager.getPremarketVolumeMax()

        if (!Utils.isActiveSession()) { // если биржа закрыта, то показать всё
            volumeMin = 0
        }

        if (!Utils.isActiveSession()) { // если биржа закрыта, то показать всё
            volumeMax = 10000000
        }

        stocks = all.filter {
            it.getPriceDouble() > min && it.getPriceDouble() < max &&
            abs(it.changePrice0145Percent) >= abs(change) &&
            it.getTodayVolume() >= volumeMin &&
            it.getTodayVolume() <= volumeMax
        }.toMutableList()

        return stocks
    }

    fun resort(): MutableList<Stock> {
        currentSort = if (currentSort == Sorting.DESCENDING) Sorting.ASCENDING else Sorting.DESCENDING
        stocks.sortBy {
            val sign = if (currentSort == Sorting.ASCENDING) 1 else -1
            it.changePrice0145Percent * sign
        }
        return stocks
    }
}