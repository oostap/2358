package com.project.ti2358.data.manager

import com.project.ti2358.service.ScreenerType
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

    var screenerTypeFrom: ScreenerType = ScreenerType.screener2300
    var screenerTypeTo: ScreenerType = ScreenerType.screenerNow

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
            it.getPriceNow() > min && it.getPriceNow() < max &&
            it.getTodayVolume() >= volumeMin &&
            it.getTodayVolume() <= volumeMax
        }.toMutableList()

        stocks.forEach { it.processScreener(screenerTypeFrom, screenerTypeTo) }
        stocks.removeAll { it.priceScreenerFrom == 0.0 || it.priceScreenerTo == 0.0 }
        stocks = stocks.filter { abs(it.changePriceScreenerPercent) >= abs(change) }.toMutableList()

        return stocks
    }

    fun resort(): MutableList<Stock> {
        currentSort = if (currentSort == Sorting.DESCENDING) Sorting.ASCENDING else Sorting.DESCENDING
        stocks.sortBy {
            val sign = if (currentSort == Sorting.ASCENDING) 1 else -1
            it.changePriceScreenerPercent * sign
        }
        return stocks
    }
}