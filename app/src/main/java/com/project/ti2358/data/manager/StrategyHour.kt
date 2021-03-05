package com.project.ti2358.data.manager

import com.project.ti2358.data.model.dto.Interval
import com.project.ti2358.data.service.SettingsManager
import com.project.ti2358.service.Sorting
import com.project.ti2358.service.Utils
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.abs

@KoinApiExtension
class StrategyHour : KoinComponent {
    private val stockManager: StockManager by inject()

    var stocks: MutableList<Stock> = mutableListOf()

    fun process(): MutableList<Stock> {
        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        val change = SettingsManager.get1005ChangePercent()
        var volumeDayPieces = SettingsManager.get1005VolumeDayPieces()

        if (!Utils.isActiveSession()) { // если биржа закрыта, то показать всё
            volumeDayPieces = 0
        }

        stocks = stockManager.stocksStream.filter { stock ->
            stock.getPriceDouble() > min && stock.getPriceDouble() < max &&
//            abs(stock.changePrice2359DayPercent) >= abs(change) &&              // изменение
            stock.getTodayVolume() >= volumeDayPieces                           // объём в шт
        } as MutableList<Stock>

        return stocks
    }

    fun resort(sort: Sorting = Sorting.ASCENDING, interval: Interval): MutableList<Stock> {
        if (interval == Interval.HOUR) {
            if (sort == Sorting.ASCENDING)
                stocks.sortBy { it.changePriceHour1Percent }
            else
                stocks.sortByDescending { it.changePriceHour1Percent }
        } else if (interval == Interval.TWO_HOURS) {
            if (sort == Sorting.ASCENDING)
                stocks.sortBy { it.changePriceHour2Percent }
            else
                stocks.sortByDescending { it.changePriceHour2Percent }
        }

        return stocks
    }
}