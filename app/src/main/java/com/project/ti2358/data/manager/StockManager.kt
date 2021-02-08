package com.project.ti2358.data.manager

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.project.ti2358.data.model.dto.Candle
import com.project.ti2358.data.model.dto.Interval
import com.project.ti2358.data.model.dto.MarketInstrument
import com.project.ti2358.data.service.MarketService
import com.project.ti2358.data.service.SettingsManager
import com.project.ti2358.data.service.StreamingService
import io.reactivex.rxjava3.kotlin.subscribeBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@KoinApiExtension
class StockManager() : KoinComponent {
    private val marketService: MarketService by inject()
    private val streamingService: StreamingService by inject()

    private var instrumentsAll: List<MarketInstrument> = emptyList()
    private var stocksAll: MutableList<Stock> = mutableListOf()

    // все акции, которые участвуют в расчётах с учётом базовой сортировки из настроек
    var stocksStream: MutableList<Stock> = mutableListOf()

    fun loadStocks() {
        val key = "all_instruments"

        val gson = GsonBuilder().create()
        val preferences = PreferenceManager.getDefaultSharedPreferences(SettingsManager.context)
        val jsonInstuments = preferences.getString(key, null)
        if (jsonInstuments != null) {
            val itemType = object : TypeToken<List<MarketInstrument>>() {}.type
            instrumentsAll = gson.fromJson(jsonInstuments, itemType)
        }

        if (instrumentsAll.isNotEmpty()) {
            afterLoadInstruments()
            return
        }

        GlobalScope.launch(Dispatchers.Main) {
            while (instrumentsAll.isEmpty()) {
                try {
                    instrumentsAll = marketService.stocks().instruments

                    val jsonInstruments = gson.toJson(instrumentsAll)
                    val editor: SharedPreferences.Editor = preferences.edit()
                    editor.putString(key, jsonInstruments)
                    editor.apply()

                    afterLoadInstruments()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(1500) // 1 sec
            }
        }
    }

    private fun afterLoadInstruments() {
        stocksAll.clear()
        for (instrument in instrumentsAll) {
            stocksAll.add(Stock(instrument))
        }
        baseSortStocks()

        resetSubscription()
    }

    fun getStockByFigi(figi: String) : Stock? {
        for (stock in stocksAll) {
            if (stock.marketInstrument.figi == figi) {
                return stock
            }
        }
        return null
    }

    private fun baseSortStocks() {
        stocksStream.clear()

        for (stock in stocksAll) {
            if (SettingsManager.isAllowCurrency(stock.marketInstrument.currency)) {
                stocksStream.add(stock)
            }
        }
    }

    private fun resetSubscription() {
        stocksStream.let { stocks ->

            streamingService
                .getCandleEventStream(
                    stocks.map { it.marketInstrument.figi },
                    Interval.DAY
                )
                .subscribeBy(
                    onNext = {
                        addCandle(it)
                    },
                    onError = {
                        it.printStackTrace()
                    }
                )

            streamingService
                .getCandleEventStream(
                    stocks.map { it.marketInstrument.figi },
                    Interval.MINUTE
                )
                .subscribeBy(
                    onNext = {
                        addCandle(it)
                    },
                    onError = {
                        it.printStackTrace()
                    }
                )

            streamingService
                .getCandleEventStream(
                    stocks.map { it.marketInstrument.figi },
                    Interval.WEEK
                )
                .subscribeBy(
                    onNext = {
                        addCandle(it)
                    },
                    onError = {
                        it.printStackTrace()
                    }
                )
        }
    }

    fun unsubscribeStock(stock: Stock, interval: Interval) {
        streamingService.getCandleEventStream(listOf(stock.marketInstrument.figi), interval)
    }

    private fun addCandle(candle: Candle) {
        for (stock in stocksStream) {
            if (stock.marketInstrument.figi == candle.figi) {
                stock.processCandle(candle)
            }
        }
    }
}
