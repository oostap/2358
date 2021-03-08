package com.project.ti2358.data.manager

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.project.ti2358.TheApplication
import com.project.ti2358.data.model.dto.Candle
import com.project.ti2358.data.model.dto.Interval
import com.project.ti2358.data.model.dto.MarketInstrument
import com.project.ti2358.data.model.dto.yahoo.YahooResponse
import com.project.ti2358.data.service.MarketService
import com.project.ti2358.data.service.StreamingAlorService
import com.project.ti2358.data.service.StreamingTinkoffService
import com.project.ti2358.data.service.ThirdPartyService
import com.project.ti2358.service.Utils
import com.project.ti2358.service.log
import io.reactivex.rxjava3.kotlin.subscribeBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@KoinApiExtension
class StockManager : KoinComponent {
    private val thirdPartyService: ThirdPartyService by inject()
    private val marketService: MarketService by inject()
    private val streamingTinkoffService: StreamingTinkoffService by inject()
    private val streamingAlorService: StreamingAlorService by inject()
    private val alorManager: AlorManager by inject()

    private var instrumentsAll: MutableList<MarketInstrument> = mutableListOf()
    private var stocksAll: MutableList<Stock> = mutableListOf()

    // все акции, которые участвуют в расчётах с учётом базовой сортировки из настроек
    var stocksStream: MutableList<Stock> = mutableListOf()

    private val gson = Gson()

    fun loadStocks(force: Boolean = false) {
        val key = "all_instruments"

        val gson = GsonBuilder().create()
        val preferences = PreferenceManager.getDefaultSharedPreferences(TheApplication.application.applicationContext)
        val jsonInstruments = preferences.getString(key, null)
        if (jsonInstruments != null) {
            val itemType = object : TypeToken<List<MarketInstrument>>() {}.type
            instrumentsAll = gson.fromJson(jsonInstruments, itemType)
        }

        if (instrumentsAll.isNotEmpty() && !force) {
            afterLoadInstruments()
            return
        }

        instrumentsAll.clear()

        GlobalScope.launch(Dispatchers.Main) {
            while (instrumentsAll.isEmpty()) {
                try {
                    instrumentsAll = marketService.stocks().instruments as MutableList<MarketInstrument>

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

    val alterNames: Map<String, String> = mapOf(
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
        "SAVE" to "спирит|жёлтый",
        "SWN" to "свин",
    )

    private fun afterLoadInstruments() {
        stocksAll.clear()

        val ignoreFigi = arrayOf("BBG00GTWPCQ0", "BBG000R3RKT8", "BBG0089KM290", "BBG000D9V7T4", "BBG000TZGXK8", "BBG001P3K000", "BBG003QRSQD3", "BBG001DJNR51", "BBG000MDCJV7", "BBG000BS9HN3", "BBG000BCNYT9", "BBG002BHBHM1", "BBG000GLG0G0", "BBG00F40L971", "BBG000BXNJ07", "BBG00HY28P97", "BBG000PCNQN7", "BBG000C1JTL6", "BBG000BGTX98", "BBG000C15114", "BBG000BB0P33", "BBG000FH5YM1", "BBG00J5LMW10", "BBG000BL4504")
        val ignoreTickers = arrayOf("AAXN", "LVGO", "TECD", "NBL", "AIMT", "CXO", "ETFC", "LOGM", "IMMU", "LM", "BMCH", "AGN", "MYL", "MYOK", "AXE", "HDS", "AGN", "SINA", "TIF", "TCS")

        for (instrument in instrumentsAll) {
            // исключить фиги, по которым не отдаёт данные
            if (instrument.figi in ignoreFigi) continue

            // исключить фиги, по которым не отдаёт данные
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
        return stocksAll.find { it.marketInstrument.figi == figi }
    }

    private fun baseSortStocks() {
        stocksStream = stocksAll.filter { SettingsManager.isAllowCurrency(it.marketInstrument.currency) }.toMutableList()
        val zone = Utils.getTimezoneCurrent()

        // загрузить цену закрытия
        GlobalScope.launch(Dispatchers.Main) {
            for (stock in stocksStream) {
                var delay: Long = 0
                var from = Utils.getLastClosingDate(true) + zone
                var to = Utils.getLastClosingDate(false) + zone

                val ticker = stock.marketInstrument.ticker
                val figi = stock.marketInstrument.figi
                val key = "closing_os_new_${ticker}_${from}"
                var deltaDay = 0

                var candle2359: Candle? = null
                val preferences = PreferenceManager.getDefaultSharedPreferences(TheApplication.application.applicationContext)
                val jsonClosingCandle = preferences.getString(key, null)
                if (jsonClosingCandle != null) {
                    candle2359 = gson.fromJson(jsonClosingCandle, Candle::class.java)
                }

                try {
                    while (candle2359 == null) {
                        delay = kotlin.random.Random.Default.nextLong(400, 600)
                        val candles = marketService.candles(figi, "1min", from, to)
                        if (candles.candles.isNotEmpty()) {
                            candle2359 = candles.candles.first()
                        } else { // если свечей нет, то сделать шаг назад во времени
                            deltaDay--
                            from = Utils.getLastClosingDate(true, deltaDay) + zone
                            to = Utils.getLastClosingDate(false, deltaDay) + zone
                            delay(delay)
                            continue
                        }

                        val data = gson.toJson(candle2359)
                        val editor: SharedPreferences.Editor = preferences.edit()
                        editor.putString(key, data)
                        editor.apply()

                        log("close price $ticker $candle2359")
                    }

                    stock.processCandle2359(candle2359)
                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                }

                if (Utils.isHighSpeedSession()) {
                    delay *= 10
                }
                delay(delay)
            }
        }

        val from = Utils.getLastClosingPostmarketUSDate() + zone
        // загрузить постмаркет
        GlobalScope.launch(Dispatchers.Main) {
            for (stock in stocksStream) {
                var delay: Long = 0
                var ticker = stock.marketInstrument.ticker
                if (ticker == "SPB@US") ticker = "SPB" // костыль, в yahoo тикер назван по-другому
                ticker = ticker.replace(".", "-")

                val key = "close_postmarket_us_new_${ticker}_${from}"

                var yahooResponse: YahooResponse? = null
                val preferences = PreferenceManager.getDefaultSharedPreferences(TheApplication.application.applicationContext)
                val jsonClosing = preferences.getString(key, null)
                if (jsonClosing != null) {
                    yahooResponse = gson.fromJson(jsonClosing, YahooResponse::class.java)
                }

                if (yahooResponse == null) {
                    val url = "https://query1.finance.yahoo.com/v10/finance/quoteSummary/${ticker}?modules=price"

                    yahooResponse = thirdPartyService.yahooPostmarket(url)
                    if (yahooResponse != null) {

                        val data = gson.toJson(yahooResponse)
                        val editor: SharedPreferences.Editor = preferences.edit()
                        editor.putString(key, data)
                        editor.apply()
                    }

                    log("yahoo $yahooResponse $url")
                    delay = kotlin.random.Random.Default.nextLong(200, 300)
                }

                if (yahooResponse != null) {
                    stock.processPostmarketPrice(yahooResponse)
                }

                if (Utils.isHighSpeedSession()) {
                    delay *= 10
                }
                delay(delay)
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

                streamingAlorService
                    .getCandleEventStream(
                        stocks,
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

                streamingAlorService
                    .getCandleEventStream(
                        stocks,
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

            } else {
                streamingTinkoffService
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

                streamingTinkoffService
                    .getCandleEventStream(
                        stocks.map { it.marketInstrument.figi },
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
                        stocks.map { it.marketInstrument.figi },
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


                streamingTinkoffService
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

                streamingTinkoffService
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
    }

    fun unsubscribeStock(stock: Stock, interval: Interval) {
        streamingTinkoffService.getCandleEventStream(listOf(stock.marketInstrument.figi), interval)
    }

    private fun addCandle(candle: Candle) {
        if (SettingsManager.isAlorQoutes()) {
            val stock = stocksStream.find { it.marketInstrument.ticker == candle.figi }
            if (stock != null) {
                stock.processCandle(candle)
                return
            }
        } else {
            val stock = stocksStream.find { it.marketInstrument.figi == candle.figi }
            if (stock != null) {
                stock.processCandle(candle)
                return
            }
        }
    }
}
