package com.project.ti2358.data.manager

import com.project.ti2358.service.Sorting
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@KoinApiExtension
class StrategyReports : KoinComponent {
    private val stockManager: StockManager by inject()

    var stocksDividend: MutableList<Stock> = mutableListOf()
    var stocksReport: MutableList<Stock> = mutableListOf()
    var stocksFDA: MutableList<Stock> = mutableListOf()

    var currentSort: Sorting = Sorting.DESCENDING

    fun process(): MutableList<Stock> {
        val all = stockManager.getWhiteStocks()

        stocksDividend = all.filter { it.dividend != null }.toMutableList()
        stocksReport = all.filter { it.report != null }.toMutableList()
        stocksFDA = all.filter { it.fda != null }.toMutableList()

        return stocksReport
    }

    fun resortReport(): MutableList<Stock> {
        currentSort = if (currentSort == Sorting.DESCENDING) Sorting.ASCENDING else Sorting.DESCENDING
        stocksReport.sortBy {
            val sign = if (currentSort == Sorting.ASCENDING) -1 else 1
            it.report?.let { r ->
                r.date * sign
            }
        }
        return stocksReport
    }

    fun resortDivs(): MutableList<Stock> {
        currentSort = if (currentSort == Sorting.DESCENDING) Sorting.ASCENDING else Sorting.DESCENDING
        stocksDividend.sortBy {
            val sign = if (currentSort == Sorting.ASCENDING) -1 else 1
            it.dividend?.let { d ->
                d.date * sign
            }
        }
        return stocksDividend
    }

    fun resortFDA(): MutableList<Stock> {
        currentSort = if (currentSort == Sorting.DESCENDING) Sorting.ASCENDING else Sorting.DESCENDING
        stocksFDA.sortBy {
            val sign = if (currentSort == Sorting.ASCENDING) -1 else 1
            it.fda?.let { d ->
                d.date * sign
            }
        }
        return stocksFDA
    }
}