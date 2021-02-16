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

    var loadClosingPriceOSDelay: Long = 0
    var loadClosingPricePostmarketUSDelay: Long = 0
    var loadClosingPricePostmarketRUDelay: Long = 0

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

        val ignoreFigi = arrayOf("BBG00GTWPCQ0", "BBG000R3RKT8", "BBG0089KM290", "BBG000D9V7T4", "BBG000TZGXK8", "BBG001P3K000", "BBG003QRSQD3", "BBG001DJNR51", "BBG000MDCJV7", "BBG000BS9HN3", "BBG000BCNYT9", "BBG002BHBHM1", "BBG000GLG0G0", "BBG00F40L971", "BBG000BXNJ07", "BBG00HY28P97", "BBG000PCNQN7", "BBG000C1JTL6", "BBG000BGTX98", "BBG000C15114", "BBG000BB0P33", "BBG000FH5YM1", "BBG00J5LMW10", "BBG000BL4504")
        val ignoreTickers = arrayOf("AAXN", "LVGO", "TECD", "NBL", "AIMT", "CXO", "ETFC", "LOGM", "IMMU", "LM", "BMCH", "AGN", "MYL", "MYOK", "AXE", "HDS", "AGN", "SINA", "TIF", "TCS")

        for (instrument in instrumentsAll) {
            // исключить фиги, по которым не отдаёт данные
            if (ignoreFigi.contains(instrument.figi)) continue

            // исключить фиги, по которым не отдаёт данные
            if (ignoreTickers.contains(instrument.ticker)) continue

            // исключить какие-то устаревшие тикеры?
            if (instrument.ticker.contains("old")) continue

            // исключить фонды тинькова
            if (instrument.figi.contains("TCS")) continue

            stocksAll.add(Stock(instrument))
        }
        baseSortStocks()

        resetSubscription()
    }

    fun getStockByFigi(figi: String): Stock? {
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

                loadClosingPriceOSDelay = stock.loadClosingOSCandle(loadClosingPriceOSDelay)
                loadClosingPricePostmarketUSDelay = stock.loadClosingPostmarketUSPrice(loadClosingPricePostmarketUSDelay)
//                loadClosingPricePostmarketRUDelay = stock.loadClosingPostmarketRUCandle(loadClosingPricePostmarketRUDelay)
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
