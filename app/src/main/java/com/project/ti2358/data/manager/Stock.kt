package com.project.ti2358.data.manager

import com.project.ti2358.data.model.dto.*
import com.project.ti2358.data.model.dto.Currency
import com.project.ti2358.data.model.dto.daager.ClosePrice
import com.project.ti2358.data.model.dto.daager.Dividend
import com.project.ti2358.data.model.dto.daager.Report
import com.project.ti2358.data.model.dto.daager.StockShort
import org.koin.core.component.KoinApiExtension
import java.util.*
import java.util.concurrent.TimeUnit

data class Stock(
    var instrument: Instrument
) {
    var alterName: String = ""
    var report: Report? = null
    var dividend: Dividend? = null
    var short: StockShort? = null
    var stockIndices: List<String>? = null

    var orderbookStream: OrderbookStream? = null

    var middlePrice: Double = 0.0
    var dayVolumeCash: Double = 0.0

    var price1000: Double = 0.0             // цена открытия премаркета РФ
    var priceTazik: Double = 0.0            // цена для утреннего тазика

    var changeOnStartTimer: Double = 0.0    // сколько % было на старте таймера для 2358

    var closePrices: ClosePrice? = null
    var candleToday: Candle? = null                               // реалтайм, дневная свеча
    var minute1728Candles: MutableList<Candle> = mutableListOf() // все свечи после 1728

    lateinit var candleHour1: Candle                             // текущая часовая свеча
    lateinit var candleHour2: Candle                             // текущая двухчасовая свеча
    
    // разница с ценой открытия премаркета
    var changePriceDayAbsolute: Double = 0.0
    var changePriceDayPercent: Double = 0.0

    // разница с ценой закрытия постмаркет US
    var changePricePostmarketAbsolute: Double = 0.0
    var changePricePostmarketPercent: Double = 0.0

    // разница с ценой закрытия ОС
    var changePrice2359DayAbsolute: Double = 0.0
    var changePrice2359DayPercent: Double = 0.0

    // разница со старта таймера
    var changePrice1728DayAbsolute: Double = 0.0
    var changePrice1728DayPercent: Double = 0.0

    // разница по 1 часовой свече
    var changePriceHour1Start: Double = 0.0
    var changePriceHour1Absolute: Double = 0.0
    var changePriceHour1Percent: Double = 0.0

    // разница по 2 часовой свече
    var changePriceHour2Start: Double = 0.0
    var changePriceHour2Absolute: Double = 0.0
    var changePriceHour2Percent: Double = 0.0

    // все минутные свечи с момента запуска приложения
    var minuteCandles: MutableList<Candle> = mutableListOf()

    fun getCurrencySymbol(): String {
        return when (instrument.currency) {
            Currency.USD -> "$"
            Currency.RUB -> "₽"
            Currency.EUR -> "€"
            Currency.GBP -> "£"
            Currency.CHF -> "₣"
            Currency.HKD -> "HK$"
            Currency.CNY -> "¥"
            Currency.JPY -> "¥"
            Currency.TRY -> "₺"
            else -> "$"
        }
    }

    @KoinApiExtension
    fun getSectorName(): String {
        var sector = closePrices?.sector?.eng ?: ""
        stockIndices?.forEach {
            sector += " | ${StockManager.stockIndex?.getShortName(it)}"
        }
        return sector
    }

    fun getReportInfo(): String {
        var info = ""
        report?.let {
            info += "О: ${it.date_format} "
            var tod = if (it.tod == "post") " 🌚" else " 🌞"
            if (it.actual_eps != null || it.actual_rev_per != null) {
                tod += "✅"
            }
            info += tod
        }

        dividend?.let {
            info += "Д: ${it.date_format}"
        }

        return info
    }

    fun processOrderbook(orderbook: OrderbookStream) {
        orderbookStream = orderbook
    }

    fun processCandle(candle: Candle) {
        when (candle.interval) {
            Interval.DAY -> {
                processDayCandle(candle)
            }
            Interval.MINUTE -> {
                processMinuteCandle(candle)
            }
            Interval.HOUR -> {
                processHour1Candle(candle)
            }
            Interval.TWO_HOURS -> {
                processHour2Candle(candle)
            }
            else -> {

            }
        }
    }

    private fun processDayCandle(candle: Candle) {
        val diffInMilli: Long = Calendar.getInstance().time.time - candle.time.time
        val diffInHours: Long = TimeUnit.MILLISECONDS.toHours(diffInMilli)

//        if (marketInstrument.ticker == "NARI") {
//            log("ALOR = ${marketInstrument.ticker} = $candle")
//        }

        if (diffInHours > 20) {
//            candleYesterday = candle
//            log(candle.toString() + instrument.ticker)
            return
        }

        candleToday = candle

        updateChangeToday()
        updateChange2359()
        updateChangePostmarket()
    }

    private fun processHour1Candle(candle: Candle) {
        candleHour1 = candle

        changePriceHour1Start = candleHour1.openingPrice
        changePriceHour1Absolute = candleHour1.closingPrice - candleHour1.openingPrice
        changePriceHour1Percent = (100 * candleHour1.closingPrice) / candleHour1.openingPrice - 100
    }

    private fun processHour2Candle(candle: Candle) {
        candleHour2 = candle

        changePriceHour2Start = candleHour2.openingPrice
        changePriceHour2Absolute = candleHour2.closingPrice - candleHour2.openingPrice
        changePriceHour2Percent = (100 * candleHour2.closingPrice) / candleHour2.openingPrice - 100
    }

    @KoinApiExtension
    private fun processMinuteCandle(candle: Candle) {
        var exists = false
        for ((index, c) in minuteCandles.withIndex()) {
            if (c.time == candle.time) {
                minuteCandles[index] = candle
                exists = true
            }
        }
        if (!exists) {
            minuteCandles.add(candle)
        }

        // проверка на стратегию 1728
        val timeCandle = Calendar.getInstance()
        timeCandle.time = candle.time
        val timeTrackStart = Strategy1728.strategyStartTime

        if (timeCandle.time >= timeTrackStart.time) {
            exists = false
            for ((index, c) in minute1728Candles.withIndex()) {
                if (c.time == candle.time) {
                    minute1728Candles[index] = candle
                    exists = true
                }
            }
            if (!exists) {
                minute1728Candles.add(candle)
            }
        }

        updateChange1728()
    }

    fun getVolume1728BeforeStart(): Int {
        return getTodayVolume() - getVolume1728AfterStart()
    }

    fun getVolume1728AfterStart(): Int {
        var volume = 0
        for (candle in minute1728Candles) {
            volume += candle.volume
        }
        return volume
    }

    fun getTodayVolume(): Int {
        return candleToday?.volume ?: 0
    }

    fun getPriceDouble(): Double {
        if (minuteCandles.isNotEmpty()) {
            return minuteCandles.last().closingPrice
        }

        var value: Double = 0.0

        closePrices?.let {
            value = it.close_post
        }

//        candleYesterday?.let {
//            value = it.closingPrice
//        }

        candleToday?.let {
            value = it.closingPrice
        }

        return value
    }

    fun getPricePostmarketUSDouble(): Double {
        return closePrices?.close_post_yahoo ?: 0.0
    }
    fun getPriceString(): String {
        val price = getPriceDouble()
        return "$price$"
    }

    fun getPrice1000String(): String {
        candleToday?.let {
            return "${it.openingPrice}$"
        }
        return "0$"
    }

    fun getPrice2359String(): String {
        closePrices?.let {
            return "${it.close_os}$"
        }
        return "0$"
    }

    fun getPrice1728String(): String {
        if (minute1728Candles.isNotEmpty()) {
            return "${minute1728Candles.first().closingPrice}$"
        }
        return "0$"
    }

    private fun updateChangeToday() {
        candleToday?.let {
            changePriceDayAbsolute = it.closingPrice - it.openingPrice
            changePriceDayPercent = (100 * it.closingPrice) / it.openingPrice - 100

            middlePrice = (it.highestPrice + it.lowestPrice ) / 2.0
            dayVolumeCash = middlePrice * it.volume

            price1000 = it.openingPrice
        }
    }

    private fun updateChangePostmarket() {
        closePrices?.let { close ->
            close.close_post_yahoo?.let {
                changePricePostmarketAbsolute = close.close_post_yahoo - close.close_os
                changePricePostmarketPercent = (100 * close.close_post_yahoo) / close.close_os - 100

                candleToday?.let { today ->
                    changePricePostmarketAbsolute = close.close_post_yahoo - today.closingPrice
                    changePricePostmarketPercent = (100 * close.close_post_yahoo) / today.closingPrice - 100
                }
            }
        }
    }

    private fun updateChange2359() {
        closePrices?.let {
            changePrice2359DayAbsolute = it.close_post - it.close_os
            changePrice2359DayPercent = (100 * it.close_post) / it.close_os - 100

            candleToday?.let { today ->
                changePrice2359DayAbsolute = today.closingPrice - it.close_os
                changePrice2359DayPercent = (100 * today.closingPrice) / it.close_os - 100
            }
        }
    }

    private fun updateChange1728() {
        if (minute1728Candles.isNotEmpty()) {
            candleToday?.let { week ->
                changePrice1728DayAbsolute = week.closingPrice - minute1728Candles.first().openingPrice
                changePrice1728DayPercent = (100 * week.closingPrice) / minute1728Candles.first().openingPrice - 100
            }
        }
    }

    fun reset1728() {
        changePrice1728DayAbsolute = 0.0
        changePrice1728DayPercent = 0.0
        minute1728Candles.clear()
    }

    fun process2359() {
        updateChange2359()
        updateChangePostmarket()
    }
}