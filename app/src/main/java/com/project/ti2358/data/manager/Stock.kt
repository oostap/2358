package com.project.ti2358.data.manager

import com.project.ti2358.data.model.dto.*
import com.project.ti2358.data.model.dto.Currency
import com.project.ti2358.data.model.dto.daager.*
import com.project.ti2358.service.toMoney
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
    var minuteCandleFixed: Candle? = null
    var priceFixed: Double = 0.0
    var changePriceFixDayAbsolute: Double = 0.0
    var changePriceFixDayPercent: Double = 0.0

    // стратегия 1728
    var priceSteps1728: StockPrice1728? = null

    // изменение с 7 до 12
    var changePrice700to1200Absolute: Double = 0.0
    var changePrice700to1200Percent: Double = 0.0
    var volume700to1200 = 0
    // изменение с 7 до 1600
    var changePrice700to1600Absolute: Double = 0.0
    var changePrice700to1600Percent: Double = 0.0
    var volume700to1600 = 0
    // изменение с 1630 до 1635
    var changePrice1630to1635Absolute: Double = 0.0
    var changePrice1630to1635Percent: Double = 0.0
    var volume1630to1635 = 0

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
        var sector = closePrices?.sector ?: ""
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

    @KoinApiExtension
    fun processCandle(candle: Candle) {
        when (candle.interval) {
            Interval.DAY -> { processDayCandle(candle) }
            Interval.MINUTE -> { processMinuteCandle(candle) }
            Interval.HOUR -> { processHour1Candle(candle) }
            Interval.TWO_HOURS -> { processHour2Candle(candle) }
            else -> { }
        }
    }

    private fun processDayCandle(candle: Candle) {
        val diffInMilli: Long = Calendar.getInstance().time.time - candle.time.time
        val diffInHours: Long = TimeUnit.MILLISECONDS.toHours(diffInMilli)

//        if (marketInstrument.ticker == "NARI") {
//            log("ALOR = ${marketInstrument.ticker} = $candle")
//        }

        if (diffInHours > 20) {
            return
        }

        candleToday = candle

        updateChangeToday()
        updateChange2359()
        updateChangePostmarket()
        updateChangeFixPrice()
    }

    private fun processHour1Candle(candle: Candle) {
        // do nothing
    }

    private fun processHour2Candle(candle: Candle) {
        // do nothing
    }

    @KoinApiExtension
    private fun processMinuteCandle(candle: Candle) {
        var exists = false
        synchronized(minuteCandles) {
            for ((index, c) in minuteCandles.withIndex()) {
                if (c.time == candle.time) {
                    minuteCandles[index] = candle
                    exists = true
                }
            }
            if (!exists) {
                minuteCandles.add(candle)
            }

            minuteCandles.sortBy { it.time }
        }

//        if (instrument.ticker in listOf<String>("CNK", "MAC", "OIS")) {
//            log("${instrument.ticker}: candles(${minuteCandles.size}), date: ${candle.time}")
//            log("${instrument.ticker}: candles(${minuteCandles.size}), date: ${minuteCandles.first().time}")
////        }
//
//        // проверка на стратегию FixPrice
//        val timeCandle = Calendar.getInstance()
//        timeCandle.time = candle.time
//        val timeTrackStart = StrategyFixPrice.strategyStartTime
//
//        if (timeCandle.time >= timeTrackStart.time) {
//            exists = false
//            for ((index, c) in minuteFixPriceCandles.withIndex()) {
//                if (c.time == candle.time) {
//                    minuteFixPriceCandles[index] = candle
//                    exists = true
//                }
//            }
//            if (!exists) {
//                minuteFixPriceCandles.add(candle)
//            }
//        }

        updateChangeFixPrice()
    }

    fun process2359() {
        updateChange2359()
        updateChangePostmarket()
    }

    fun getVolumeFixPriceBeforeStart(): Int {
        return getTodayVolume() - getVolumeFixPriceAfterStart()
    }

    fun getVolumeFixPriceAfterStart(): Int {
        var volume = 0
        for (candle in minuteCandles) {
            if (candle.time >= minuteCandleFixed?.time) {
                volume += candle.volume
            }
        }
        return volume
    }

    fun getTodayVolume(): Int {
        return candleToday?.volume ?: 0
    }

    fun getPriceDouble(): Double {
        var value = 0.0

        closePrices?.let {
            value = it.post
        }

        candleToday?.let {
            value = it.closingPrice
        }

        return value
    }

    fun getPricePostmarketUSDouble(): Double {
        return closePrices?.yahoo ?: 0.0
    }

    fun getPriceString(): String {
        return getPriceDouble().toMoney(this)
    }

    fun getPricePost1000String(): String {
        closePrices?.let {
            return it.post.toMoney(this)
        }
        return "0$"
    }

    fun getPrice2359String(): String {
        closePrices?.let {
            return it.os.toMoney(this)
        }
        return "0$"
    }

    fun getPriceFixPriceString(): String {
        return priceFixed.toMoney(this)
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
            close.yahoo?.let {
                changePricePostmarketAbsolute = close.yahoo - close.os
                changePricePostmarketPercent = (100 * close.yahoo) / close.os - 100

                candleToday?.let { today ->
                    changePricePostmarketAbsolute = close.yahoo - today.closingPrice
                    changePricePostmarketPercent = (100 * close.yahoo) / today.closingPrice - 100
                }
            }
        }
    }

    private fun updateChange2359() {
        closePrices?.let {
            changePrice2359DayAbsolute = it.post - it.os
            changePrice2359DayPercent = (100 * it.post) / it.os - 100

            candleToday?.let { today ->
                changePrice2359DayAbsolute = today.closingPrice - it.os
                changePrice2359DayPercent = (100 * today.closingPrice) / it.os - 100
            }
        }
    }

    private fun updateChangeFixPrice() {
        val currentPrice = getPriceDouble()
        changePriceFixDayAbsolute = currentPrice - priceFixed
        changePriceFixDayPercent = currentPrice / priceFixed * 100.0 - 100.0
    }

    fun resetFixPrice() {
        changePriceFixDayAbsolute = 0.0
        changePriceFixDayPercent = 0.0
        priceFixed = getPriceDouble()
        if (minuteCandles.isNotEmpty()) {
            minuteCandleFixed = minuteCandles.last()
        }
    }
}