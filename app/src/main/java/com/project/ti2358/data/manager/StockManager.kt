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
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
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
    private val strategyTazikEndless: StrategyTazikEndless by inject()
    private val strategyBlacklist: StrategyBlacklist by inject()
    private val strategyFavorites: StrategyFavorites by inject()
    private val strategyRocket: StrategyRocket by inject()
    private val strategyFixPrice: StrategyFixPrice by inject()

    private var instrumentsAll: MutableList<Instrument> = mutableListOf()
    private var stocksAll: MutableList<Stock> = mutableListOf()

    // все акции, которые участвуют в расчётах с учётом базовой сортировки по валюте $
    private var stocksStream: MutableList<Stock> = mutableListOf()

    var indices: MutableList<Index> = mutableListOf()
    var stockClosePrices: Map<String, ClosePrice> = mutableMapOf()
    var stockReports: Map<String, ReportStock> = mutableMapOf()
    var stockShorts: Map<String, StockShort> = mutableMapOf()
    var stockPrice1728: Map<String, StockPrice1728>? = mutableMapOf()
    var stockMorning: Map<String, Any> = mutableMapOf()

    private val gson = Gson()

    companion object {
        var stockIndexComponents: StockIndexComponents? = null
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
        var reloadStocks = force
        val preferences = PreferenceManager.getDefaultSharedPreferences(TheApplication.application.applicationContext)
        val keyStartUp = "start_ups"
        var countStartUps = preferences.getInt(keyStartUp, 4)
        countStartUps++
        if (countStartUps % 5 == 0) reloadStocks = true
        val editor: SharedPreferences.Editor = preferences.edit()
        editor.putInt(keyStartUp, countStartUps)
        editor.apply()

        val key = "all_instruments"
        var jsonInstruments = preferences.getString(key, null)
        if (jsonInstruments != null) {
            val itemType = object : TypeToken<List<Instrument>>() {}.type
            instrumentsAll = synchronizedList(gson.fromJson(jsonInstruments, itemType))
        }

        if (instrumentsAll.isNotEmpty() && !reloadStocks) {
            afterLoadInstruments()
            return
        }

        instrumentsAll.clear()

        GlobalScope.launch(Dispatchers.Default) {
            while (instrumentsAll.isEmpty()) {
                try {
                    instrumentsAll = synchronizedList(marketService.stocks().instruments as MutableList<Instrument>)
                    jsonInstruments = gson.toJson(instrumentsAll)
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
        GlobalScope.launch(Dispatchers.Default) {
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
        var tries = 10
        while (tries > 0) {
            try {
                stockClosePrices = synchronizedMap(thirdPartyService.daagerClosePrices() as MutableMap<String, ClosePrice>)
                stocksAll.forEach {
                    it.apply {
                        it.closePrices = stockClosePrices[it.ticker]
                        it.updateChange2300()
                    }
                }
                log(stockReports.toString())
                break
            } catch (e: Exception) {
                e.printStackTrace()
                log("daager ClosePrices not reached")
            }
            delay(10000)
            tries--
        }
    }

    private suspend fun reloadStockIndices() {
        try {
            stockIndexComponents = thirdPartyService.daagerStockIndices()
            stocksAll.forEach { it.apply {
                stockIndices = stockIndexComponents?.data?.get(it.ticker)
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
                it.report = stockReports[it.ticker]?.report
                it.dividend = stockReports[it.ticker]?.dividend
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
                it.short = stockShorts[it.ticker]
            }
            log(stockShorts.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            log("daager Shorts not reached")
        }
    }

    suspend fun reloadStockPrice1728() {
        try {
            stockPrice1728 = thirdPartyService.daagerStock1728()
            stocksAll.forEach {
                stockPrice1728?.let { stock1728 ->
                    if (it.ticker in stock1728)
                        it.priceSteps1728 = stock1728[it.ticker]
                }
            }
            log("daager StockPrice1728: $stockPrice1728")
        } catch (e: Exception) {
            e.printStackTrace()
            log("daager StockPrice1728 not reached")
        }
    }

    suspend fun reloadMorningCompanies() {
        try {
            stockMorning = thirdPartyService.daagerMorningCompanies()
            stocksAll.forEach {
                it.morning = stockMorning[it.ticker]
            }
            log(stockMorning.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            log("daager MorningCompanies not reached")
        }
    }

    private val alterNames: Map<String, String> = mapOf(
        "SPCE" to "галя вирджин",
        "ZYNE" to "зина",
        "COTY" to "кот",
        "M" to "мася",
        "BABA" to "баба",
        "CCL" to "карнавал",
        "HEAR" to "черепаха",
        "CNK" to "кино синька",
        "ENDP" to "эндо",
        "GTHX" to "перч тварь",
        "AIMT" to "арахис",
        "SAVE" to "спирит жёлтый",
        "SPR" to "спирит синий",
        "SWN" to "свин",
        "SRPT" to "сергей копытов",

        "REGI" to "региша",
        "NTLA" to "тля",
        "ZYXI" to "зукся",
        "ZGNX" to "зугиня",
        "BBBY" to "бабайка",
        "ARCT" to "конь скотина",
        "PBI" to "пибай",
        
        "BIDU" to "байда беда",
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
                alterName = alterNames[ticker] ?: ""
            })
        }
        processStocks()
        resetSubscription(stocksStream)
    }

    fun getStockByFigi(figi: String): Stock? {
        return stocksAll.find { it.figi == figi }
    }

    fun getStockByTicker(ticker: String): Stock? {
        return stocksAll.find { it.ticker == ticker }
    }

    private fun processStocks() {
        stocksStream = synchronizedList(stocksAll.filter { SettingsManager.isAllowCurrency(it.instrument.currency) }.toMutableList())
        strategyFavorites.process()
        strategyBlacklist.process()
        strategyFixPrice.restartStrategy()

        // загрузить цену закрытия
        GlobalScope.launch(Dispatchers.Default) {
            while (true) {
                try {
                    reloadClosePrices()
                    reloadReports()
                    reloadShortInfo()
                    reloadStockIndices()
                    reloadStockPrice1728()
                    reloadMorningCompanies()
                    break
                } catch (e: Exception) {
                    e.printStackTrace()
                    log("daager some OpenAPI not reached")
                }
                delay(1000)
            }
        }
    }

    private fun resetSubscription(stocks: List<Stock>, day: Boolean = true, minute: Boolean = true) {
        if (SettingsManager.getAlorQuotes()) {
            streamingAlorService
                .getCandleEventStream(
                    if (minute) stocks else emptyList(),
                    Interval.MINUTE
                )
                .onBackpressureBuffer()
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
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
                    if (minute) stocks.map { it.figi } else emptyList(),
                    Interval.MINUTE
                )
                .onBackpressureBuffer()
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onNext = {
                        addCandle(it)
                    },
                    onError = {
                        it.printStackTrace()
                    }
                )
        }

        // дневные лучше всегда брать с ТИ, алор отдаёт очень долго, нужны только для объёмы и когда минутных ещё нет
        streamingTinkoffService
            .getCandleEventStream(
                if (day) stocks.map { it.figi } else emptyList(),
                Interval.DAY
            )
            .onBackpressureBuffer()
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onNext = {
                    addCandle(it)
                },
                onError = {
                    it.printStackTrace()
                }
            )
    }

    fun subscribeStockOrderbook(stock: Stock) {
        if (SettingsManager.getAlorOrdebook()) {
            streamingAlorService
                .getOrderEventStream(
                    listOf(stock),
                    20
                )
                .onBackpressureBuffer()
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onNext = {
                        addOrderbook(it)
                    },
                    onError = {
                        it.printStackTrace()
                    }
                )
        } else {
            streamingTinkoffService
                .getOrderEventStream(
                    listOf(stock.figi),
                    20
                )
                .onBackpressureBuffer()
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onNext = {
                        addOrderbook(it)
                    },
                    onError = {
                        it.printStackTrace()
                    }
                )
        }
    }

    fun unsubscribeStockOrderbook(stock: Stock) {
        if (SettingsManager.getAlorOrdebook()) {
            streamingAlorService.unsubscribeOrderBookEventsStream(stock, 20)
        } else {
            streamingTinkoffService.unsubscribeOrderEventsStream(stock.figi, 20)
        }
    }

    private fun addOrderbook(orderbookStream: OrderbookStream) {
        val stock = stocksStream.find { it.figi == orderbookStream.figi || it.ticker == orderbookStream.figi }
        stock?.processOrderbook(orderbookStream)
    }

    @Synchronized
    private fun addCandle(candle: Candle) {
        val stock: Stock? = stocksStream.find { it.ticker == candle.figi || it.figi == candle.figi }
        stock?.let {
            it.processCandle(candle)

            if (candle.interval == Interval.MINUTE) {
                strategyTazik.processStrategy(it, candle)
                strategyTazikEndless.processStrategy(it, candle)
                strategyRocket.processStrategy(it)
            }

            if (candle.interval == Interval.DAY) { // получить дневные свечи 1 раз по всем тикерам и отключиться
                streamingTinkoffService.unsubscribeCandleEventsStream(stock.figi, Interval.DAY)
            }
        }
    }
}
