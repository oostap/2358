package com.project.ti2358.data.manager

import com.project.ti2358.service.PurchaseStatus
import com.project.ti2358.service.Sorting
import com.project.ti2358.service.Utils
import com.project.ti2358.service.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

@KoinApiExtension
class StrategyFixPrice() : KoinComponent {
    private val stockManager: StockManager by inject()
    var stocks: MutableList<Stock> = mutableListOf()
    var stocksSelected: MutableList<Stock> = mutableListOf()

    var currentSort: Sorting = Sorting.DESCENDING
    var strategyStartTime: Calendar = Calendar.getInstance()
    var fixTimes: List<Calendar> = mutableListOf()

    fun process(): MutableList<Stock> {
        val all = stockManager.getWhiteStocks()
        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()
        stocks = all.filter { stock -> stock.getPriceNow() > min && stock.getPriceNow() < max }.toMutableList()
        return stocks
    }

    fun reloadSchedule() {
        fixTimes = SettingsManager.getFixPriceTimes()
    }

    fun restartStrategy() {
        reloadSchedule()
        fixPrice()

        GlobalScope.launch(StockManager.stockContext) {
            while (true) {
                delay(1000) // 1 sec

                val msk = Utils.getTimeMSK()
                for (time in fixTimes) {
                    if (time.get(Calendar.HOUR_OF_DAY) == msk.get(Calendar.HOUR_OF_DAY) &&
                        time.get(Calendar.MINUTE) == msk.get(Calendar.MINUTE) &&
                        time.get(Calendar.SECOND) == msk.get(Calendar.SECOND)) {
                        fixPrice()
                    }
                }
            }
        }
    }

    private fun fixPrice() {
        strategyStartTime = Calendar.getInstance()
        strategyStartTime.set(Calendar.SECOND, 0)

        val all = stockManager.getWhiteStocks()
        all.forEach {
            it.resetFixPrice()
        }
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