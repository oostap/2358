package com.project.ti2358.data.service

import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.project.ti2358.data.manager.PurchaseStock
import com.project.ti2358.data.model.dto.Candle
import com.project.ti2358.data.model.dto.Interval
import com.project.ti2358.data.model.dto.MarketInstrument
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.kotlin.subscribeBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.roundToInt
import kotlin.random.Random

@KoinApiExtension
class Strategy1830() : KoinComponent {
    private val stockManager: StockManager by inject()
    var stocks: MutableList<Stock> = mutableListOf()
    var stocksSelected: MutableList<Stock> = mutableListOf()
    var stocksToPurchase: MutableList<PurchaseStock> = mutableListOf()

    public fun process() : MutableList<Stock> {
        stocks.clear()

        val all = stockManager.stocksStream
        for (stock in all) {
//            if (SettingsManager.isAllowCurrency(stock.marketInstrument.currency)) {
            stocks.add(stock)
//            }
        }

        stocks.sortBy { it.changePriceDayPercent }

        return stocks
    }

    public fun setSelected(stock: Stock, value : Boolean) {
        if (value) {
            stocksSelected.remove(stock)
        } else {
            if (!stocksSelected.contains(stock))
                stocksSelected.add(stock)
        }
        stocksSelected.sortBy { it.changePriceDayPercent }
    }

    public fun isSelected(stock: Stock) : Boolean {
        return stocksSelected.contains(stock)
    }

    public fun getPurchaseStock() : MutableList<PurchaseStock> {
        stocksToPurchase.clear()
        for (stock in stocksSelected) {
            stocksToPurchase.add(PurchaseStock(stock))
        }

        val totalMoney : Double = SettingsManager.get2358PurchaseVolume().toDouble()
        var onePiece : Double = totalMoney / stocksToPurchase.size

        for (stock in stocksToPurchase) {
            stock.lots = (onePiece / stock.stock.todayDayCandle.closingPrice).roundToInt()
        }

        return stocksToPurchase
    }
}