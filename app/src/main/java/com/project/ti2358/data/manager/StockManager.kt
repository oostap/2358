package com.project.ti2358.data.manager

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.project.ti2358.TheApplication
import com.project.ti2358.data.model.dto.Candle
import com.project.ti2358.data.model.dto.Interval
import com.project.ti2358.data.model.dto.Instrument
import com.project.ti2358.data.model.dto.OrderbookStream
import com.project.ti2358.data.model.dto.daager.*
import com.project.ti2358.data.service.MarketService
import com.project.ti2358.data.service.StreamingAlorService
import com.project.ti2358.data.service.StreamingTinkoffService
import com.project.ti2358.data.service.ThirdPartyService
import com.project.ti2358.service.log
import io.reactivex.rxjava3.kotlin.subscribeBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Collections.synchronizedList
import java.util.Collections.synchronizedMap

@KoinApiExtension
class StockManager : KoinComponent {
    private val thirdPartyService: ThirdPartyService by inject()
    private val marketService: MarketService by inject()
    private val streamingTinkoffService: StreamingTinkoffService by inject()
    private val streamingAlorService: StreamingAlorService by inject()
    private val strategyTazik: StrategyTazik by inject()
    private val strategyBlacklist: StrategyBlacklist by inject()
    private val strategyRocket: StrategyRocket by inject()

    private var instrumentsAll: MutableList<Instrument> = mutableListOf()
    private var stocksAll: MutableList<Stock> = mutableListOf()

    // все акции, которые участвуют в расчётах с учётом базовой сортировки по валюте $
    private var stocksStream: MutableList<Stock> = mutableListOf()

    var indices: MutableList<Index> = mutableListOf()
    var stockClosePrices: Map<String, ClosePrice> = mutableMapOf()
    var stockReports: Map<String, ReportStock> = mutableMapOf()
    var stockShorts: Map<String, StockShort> = mutableMapOf()
    var stockPrice1728: Map<String, StockPrice1728> = mutableMapOf()

    private val gson = Gson()

    companion object {
        var stockIndex: StockIndex? = null
    }

    fun getAllStocks(): MutableList<Stock> {
        return stocksStream
    }

    @Synchronized
    fun getWhiteStocks(): MutableList<Stock> {
        val blacklist = strategyBlacklist.getBlacklistStocks()
        val all = stocksStream
        return all.filter { it !in blacklist }.toMutableList()
    }

    fun loadStocks(force: Boolean = false) {
        val key = "all_instruments"
        val preferences = PreferenceManager.getDefaultSharedPreferences(TheApplication.application.applicationContext)
        var jsonInstruments = preferences.getString(key, null)
        if (jsonInstruments != null) {
            val itemType = object : TypeToken<List<Instrument>>() {}.type
            instrumentsAll = synchronizedList(gson.fromJson(jsonInstruments, itemType))
        }

        if (instrumentsAll.isNotEmpty() && !force) {
            afterLoadInstruments()
            return
        }

        instrumentsAll.clear()

        GlobalScope.launch(Dispatchers.Main) {
            while (instrumentsAll.isEmpty()) {
                try {
                    instrumentsAll = synchronizedList(marketService.stocks().instruments as MutableList<Instrument>)
                    jsonInstruments = gson.toJson(instrumentsAll)
                    val editor: SharedPreferences.Editor = preferences.edit()
                    editor.putString(key, jsonInstruments)
                    editor.apply()
                    afterLoadInstruments()
                    break
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(1000) // 1 sec
            }
        }
    }

    fun startUpdateIndices() {
        GlobalScope.launch(Dispatchers.Main) {
            while (true) {
                try {
                    indices = synchronizedList(thirdPartyService.daagerIndices() as MutableList<Index>)
                } catch (e: Exception) {
                    log("daager Indices not reached")
                    delay(5000)
                    continue
                }

                delay(1000 * 30)
            }
        }
    }

    suspend fun reloadClosePrices() {
        try {
            stockClosePrices = synchronizedMap(thirdPartyService.daagerClosePrices() as MutableMap<String, ClosePrice>)
            stocksAll.forEach { it.apply {
                it.closePrices = stockClosePrices[it.instrument.ticker]
            }}
            log(stockReports.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            log("daager ClosePrices not reached")
        }
    }

    private suspend fun reloadStockIndices() {
        try {
            stockIndex = thirdPartyService.daagerStockIndices()
            stocksAll.forEach { it.apply {
                stockIndices = stockIndex?.data?.get(it.instrument.ticker)
            }}
            log(stockReports.toString())
        } catch (e: Exception) {
            log("daager StockIndex not reached")
        }
    }

    suspend fun reloadReports() {
        try {
            stockReports = thirdPartyService.daagerReports()
            stocksAll.forEach {
                it.report = stockReports[it.instrument.ticker]?.report
                it.dividend = stockReports[it.instrument.ticker]?.dividend
            }
            log(stockReports.toString())
        } catch (e: Exception) {
            log("daager Reports not reached")
        }
    }

    suspend fun reloadShortInfo() {
        try {
            stockShorts = thirdPartyService.daagerShortInfo()
            stocksAll.forEach {
                it.short = stockShorts[it.instrument.ticker]
            }
            log(stockShorts.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            log("daager Shorts not reached")
        }
    }

    private suspend fun reloadStockPrice1728() {
        try {
            stockPrice1728 = thirdPartyService.daagerStock1728()
            stocksAll.forEach {
                it.priceSteps1728 = stockPrice1728[it.instrument.ticker]
            }
            log("daager StockPrice1728: $stockPrice1728")
        } catch (e: Exception) {
            e.printStackTrace()
            log("daager StockPrice1728 not reached")
        }
    }

    private val alterNames: Map<String, String> = mapOf(
        "SPCE" to "галя|вирджин",
        "ZYNE" to "зина",
        "COTY" to "кот",
        "M" to "мася",
        "BABA" to "баба",
        "CCL" to "карнавал",
        "HEAR" to "черепаха",
        "CNK" to "кино|синька",
        "ENDP" to "эндо",
        "GTHX" to "перч|тварь",
        "AIMT" to "арахис",
        "SAVE" to "спирит жёлтый",
        "SPR" to "спирит синий",
        "SWN" to "свин",
        "SRPT" to "сергей копытов",
    )

    private fun afterLoadInstruments() {
        stocksAll.clear()

        val ignoreFigi = arrayOf("BBG00GTWPCQ0", "BBG000R3RKT8", "BBG0089KM290", "BBG000D9V7T4", "BBG000TZGXK8", "BBG001P3K000", "BBG003QRSQD3", "BBG001DJNR51", "BBG000MDCJV7", "BBG000BS9HN3", "BBG000BCNYT9", "BBG002BHBHM1", "BBG000GLG0G0", "BBG00F40L971", "BBG000BXNJ07", "BBG00HY28P97", "BBG000PCNQN7", "BBG000C1JTL6", "BBG000BGTX98", "BBG000C15114", "BBG000BB0P33", "BBG000FH5YM1", "BBG00J5LMW10", "BBG000BL4504")
        val ignoreTickers = arrayOf("AAXN", "LVGO", "TECD", "NBL", "AIMT", "CXO", "ETFC", "LOGM", "IMMU", "LM", "BMCH", "AGN", "MYL", "MYOK", "AXE", "HDS", "AGN", "SINA", "TIF", "TCS")

        for (instrument in instrumentsAll) {
            // исключить фиги, по которым не отдаёт данные
            if (instrument.figi in ignoreFigi) continue

            // исключить тикеры, по которым не отдаёт данные
            if (instrument.ticker in ignoreTickers) continue

            // исключить какие-то устаревшие тикеры?
            if ("old" in instrument.ticker) continue

            // исключить фонды тинькова
            if ("TCS" in instrument.figi) continue

            stocksAll.add(Stock(instrument).apply {
                alterName = alterNames[instrument.ticker] ?: ""
            })
        }
        baseSortStocks()
        resetSubscription()
    }

    fun getStockByFigi(figi: String): Stock? {
        return stocksAll.find { it.instrument.figi == figi }
    }

    private fun baseSortStocks() {
        val blacklist = strategyBlacklist.getBlacklistStocks()
        stocksStream = stocksAll.filter { SettingsManager.isAllowCurrency(it.instrument.currency) && it !in blacklist }.toMutableList()

        // загрузить цену закрытия
        GlobalScope.launch(Dispatchers.Main) {
            while (true) {
                try {
                    reloadClosePrices()
                    reloadReports()
                    reloadShortInfo()
                    reloadStockIndices()
                    reloadStockPrice1728()
                    break
                } catch (e: Exception) {
                    e.printStackTrace()
                    log("daager ClosePrices not reached")
                }
                delay(1000)
            }

            stocksStream.forEach {
                it.process2359()
            }
        }
    }

    private fun resetSubscription() {
        stocksStream.let { stocks ->
            if (SettingsManager.isAlorQoutes()) {
                streamingAlorService
                    .getCandleEventStream(
                        stocks,
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

                streamingAlorService
                    .getCandleEventStream(
                        stocks,
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
            } else {
                streamingTinkoffService
                    .getCandleEventStream(
                        stocks.map { it.instrument.figi },
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

                streamingTinkoffService
                    .getCandleEventStream(
                        stocks.map { it.instrument.figi },
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
            }
        }
    }

    fun subscribeStockOrderbook(stock: Stock) {
        streamingTinkoffService
            .getOrderEventStream(
                listOf(stock.instrument.figi),
                20
            )
            .subscribeBy(
                onNext = {
                    addOrderbook(it)
                },
                onError = {
                    it.printStackTrace()
                }
            )
    }

    fun unsubscribeStockOrderbook(stock: Stock) {
        streamingTinkoffService.unsubscribeOrderEventsStream(stock.instrument.figi, 20)
    }

    fun unsubscribeStock(stock: Stock, interval: Interval) {
        streamingTinkoffService.getCandleEventStream(listOf(stock.instrument.figi), interval)
    }

    private fun addOrderbook(orderbookStream: OrderbookStream) {
        val stock = stocksStream.find { it.instrument.figi == orderbookStream.figi }
        stock?.processOrderbook(orderbookStream)
    }

    @Synchronized
    private fun addCandle(candle: Candle) {
        val stock: Stock?
        if (SettingsManager.isAlorQoutes() && (candle.interval == Interval.MINUTE || candle.interval == Interval.DAY)) {
            stock = stocksStream.find { it.instrument.ticker == candle.figi }
            stock?.processCandle(candle)
        } else {
            stock = stocksStream.find { it.instrument.figi == candle.figi }
            stock?.processCandle(candle)
        }

        stock?.let {
            if (candle.interval == Interval.DAY) {
                strategyTazik.processStrategy(stock, candle)
            }

            if (candle.interval == Interval.MINUTE) {
                strategyRocket.processStrategy(stock)
            }
        }
    }
}
