package com.project.ti2358.data.manager

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.project.ti2358.TheApplication
import com.project.ti2358.data.model.dto.Candle
import com.project.ti2358.data.model.dto.Interval
import com.project.ti2358.data.model.dto.Instrument
import com.project.ti2358.data.model.dto.daager.ClosePrice
import com.project.ti2358.data.model.dto.daager.Index
import com.project.ti2358.data.model.dto.daager.ReportStock
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
    private val strategyRocket: StrategyRocket by inject()

    private var instrumentsAll: MutableList<Instrument> = mutableListOf()
    private var stocksAll: MutableList<Stock> = mutableListOf()
    var indexAll: MutableList<Index> = mutableListOf()

    private var stockClosePrices: Map<String, ClosePrice> = mutableMapOf()
    private var stockReports: Map<String, ReportStock> = mutableMapOf()
    private var stockShorts: Map<String, Short> = mutableMapOf()
    // все акции, которые участвуют в расчётах с учётом базовой сортировки из настроек
    var stocksStream: MutableList<Stock> = mutableListOf()

    private val gson = Gson()

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
                    indexAll = synchronizedList(thirdPartyService.daagerIndices() as MutableList<Index>)
                } catch (e: Exception) {
                    log("daager Indices not reached")
                    delay(5000)
                    continue
                }

                delay(1000 * 30)
            }
        }
    }

    suspend fun reloadReports() {
        try {
            stockReports = thirdPartyService.daagerReports()
            stocksAll.forEach { it.apply {
                report = stockReports[it.instrument.ticker]?.report
                dividend = stockReports[it.instrument.ticker]?.dividend
            }}
            log(stockReports.toString())
        } catch (e: Exception) {
            log("daager Reports not reached")
        }
    }

    suspend fun reloadShortInfo() {
        try {
            stockShorts = thirdPartyService.daagerShortInfo()
            stocksAll.forEach { it.short = stockShorts[it.instrument.ticker] }
            log(stockShorts.toString())
        } catch (e: Exception) {
            log("daager Shorts not reached")
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
        stocksStream = stocksAll.filter { SettingsManager.isAllowCurrency(it.instrument.currency) }.toMutableList()

        // загрузить цену закрытия
        GlobalScope.launch(Dispatchers.Main) {
            while (true) {
                try {
                    stockClosePrices = synchronizedMap(thirdPartyService.daagerClosePrices() as MutableMap<String, ClosePrice>)
                    reloadReports()
                    reloadShortInfo()
                    break
                } catch (e: Exception) {
                    e.printStackTrace()
                    log("daager ClosePrices not reached")
                }
                delay(1000)
            }

            stocksStream.forEach {
                it.closePrices = stockClosePrices[it.instrument.ticker]
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

            streamingTinkoffService
                .getCandleEventStream(
                    stocks.map { it.instrument.figi },
                    Interval.HOUR
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
                    Interval.TWO_HOURS
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
        streamingTinkoffService.getCandleEventStream(listOf(stock.instrument.figi), interval)
    }

    private fun addCandle(candle: Candle) {
        if (SettingsManager.isAlorQoutes() && (candle.interval == Interval.MINUTE || candle.interval == Interval.DAY)) {
            val stock = stocksStream.find { it.instrument.ticker == candle.figi }
            stock?.processCandle(candle)
        } else {
            val stock = stocksStream.find { it.instrument.figi == candle.figi }
            stock?.processCandle(candle)
        }

        if (candle.interval == Interval.DAY) {
            val stock = stocksStream.find { it.instrument.figi == candle.figi || it.instrument.ticker == candle.figi }
            stock?.let {
                strategyTazik.processStrategy(stock)
                strategyRocket.processStrategy(stock)
            }
        }
    }
}
