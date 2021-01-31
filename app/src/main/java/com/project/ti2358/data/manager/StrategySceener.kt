package com.project.ti2358.data.service

import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.project.ti2358.data.model.dto.Candle
import com.project.ti2358.data.model.dto.Interval
import com.project.ti2358.data.model.dto.MarketInstrument
import com.project.ti2358.service.Sorting
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.kotlin.subscribeBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.random.Random

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