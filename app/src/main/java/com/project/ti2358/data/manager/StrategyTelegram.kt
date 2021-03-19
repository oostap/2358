package com.project.ti2358.data.manager

import com.project.ti2358.service.Sorting
import com.project.ti2358.service.Utils
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.abs


@KoinApiExtension
class StrategyTelegram : KoinComponent {
    private val stockManager: StockManager by inject()

    var stocks: MutableList<Stock> = mutableListOf()
    var stocksSelected: MutableList<Stock> = mutableListOf()
    var currentSort: Sorting = Sorting.DESCENDING

    fun start() {
        // TODO
    }

    fun process(): MutableList<Stock> {
        val all = stockManager.getWhiteStocks()
        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()
        val change = SettingsManager.getPremarketChangePercent()
        var volumeDayPieces = SettingsManager.getPremarketVolume()

        if (!Utils.isActiveSession()) { // если биржа закрыта, то показать всё
            volumeDayPieces = 0
        }

        stocks = all.filter { stock ->
            stock.getPriceDouble() > min && stock.getPriceDouble() < max &&
            abs(stock.changePrice2359DayPercent) >= abs(change) &&              // изменение
            stock.getTodayVolume() >= volumeDayPieces                           // объём в шт
        }.toMutableList()

        return stocks
    }

    fun resort(): MutableList<Stock> {
        currentSort = if (currentSort == Sorting.DESCENDING) Sorting.ASCENDING else Sorting.DESCENDING
        stocks.sortBy {
            val sign = if (currentSort == Sorting.ASCENDING) 1 else -1
            it.changePrice2359DayPercent * sign
        }
        return stocks
    }
}