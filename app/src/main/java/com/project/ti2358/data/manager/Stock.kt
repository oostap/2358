package com.project.ti2358.data.manager

import com.project.ti2358.data.model.dto.*
import com.project.ti2358.data.model.dto.Currency
import com.project.ti2358.data.model.dto.daager.*
import com.project.ti2358.service.ScreenerType
import com.project.ti2358.service.toMoney
import org.koin.core.component.KoinApiExtension
import java.util.*
import java.util.concurrent.TimeUnit

data class Stock(var instrument: Instrument) {
    var ticker = instrument.ticker
    var figi = instrument.figi

    var alterName: String = ""
    var report: Report? = null
    var dividend: Dividend? = null
    var short: StockShort? = null
    var stockIndices: Map<String, Double>? = null
    var morning: Any? = null

    var orderbookStream: OrderbookStream? = null

    var middlePrice: Double = 0.0
    var dayVolumeCash: Double = 0.0

    var changeOnStartTimer: Double = 0.0    // сколько % было на старте таймера для 2358

    var closePrices: ClosePrice? = null
    var candleToday: Candle? = null                               // реалтайм, дневная свеча

    // разница с ценой закрытия ОС
    var changePrice2300DayAbsolute: Double = 0.0
    var changePrice2300DayPercent: Double = 0.0

    // разница со старта таймера
    var needToFixPrice: Boolean = false
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
    // изменение с 1628 до 1632
    var changePrice1625to1632Absolute: Double = 0.0
    var changePrice1625to1632Percent: Double = 0.0
    var volume1625to1632 = 0

    // все минутные свечи с момента запуска приложения
    var minuteCandles: MutableList<Candle> = mutableListOf()

    var priceScreenerFrom: Double = 0.0
    var priceScreenerTo: Double = 0.0

    var changePriceScreenerAbsolute: Double = 0.0
    var changePriceScreenerPercent: Double = 0.0

    fun processScreener(from: ScreenerType, to: ScreenerType) {
        priceScreenerFrom = when (from) {
            ScreenerType.screener0145 -> getPrice0145()
            ScreenerType.screener0300 -> getPrice0300()
            ScreenerType.screener2300 -> getPrice2300()
            ScreenerType.screener0700 -> getPrice1000()
            ScreenerType.screenerNow -> getPriceNow()
        }

        priceScreenerTo = when (to) {
            ScreenerType.screener0145 -> getPrice0145()
            ScreenerType.screener0300 -> getPrice0300()
            ScreenerType.screener2300 -> getPrice2300()
            ScreenerType.screener0700 -> getPrice1000()
            ScreenerType.screenerNow -> getPriceNow()
        }

        changePriceScreenerAbsolute = priceScreenerTo - priceScreenerFrom
        changePriceScreenerPercent = priceScreenerTo / priceScreenerFrom * 100.0 - 100.0
    }

    @KoinApiExtension
    fun getTickerLove(): String {
        var t = ticker
        if (StrategyFavorites.stocksSelected.find { it.ticker == t } != null) {
            t += "❤️"
        }

        if (morning != null) {
            t += "🕖"
        }
        return t
    }

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
            sector += " | %s %.2f%%".format(Locale.US, StockManager.stockIndexComponents?.getShortName(it.key), it.value)
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
    @Synchronized
    fun processCandle(candle: Candle) {
        when (candle.interval) {
            Interval.DAY -> processDayCandle(candle)
            Interval.MINUTE -> processMinuteCandle(candle)
            Interval.HOUR -> processHour1Candle(candle)
            Interval.TWO_HOURS -> processHour2Candle(candle)
            else -> { }
        }
    }

    @Synchronized
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
        updateChange2300()
        updateChangeFixPrice()
    }

    private fun processHour1Candle(candle: Candle) {
        // do nothing
    }

    private fun processHour2Candle(candle: Candle) {
        // do nothing
    }

    @KoinApiExtension
    @Synchronized
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

        updateChangeFixPrice()
    }

    fun getVolumeFixPriceBeforeStart(): Int {
        return getTodayVolume() - getVolumeFixPriceAfterStart()
    }

    fun getVolumeFixPriceAfterStart(): Int {
        var volume = 0
        for (candle in minuteCandles) {
            if (minuteCandleFixed != null) {
                if (candle.time >= minuteCandleFixed?.time) {
                    volume += candle.volume
                }
            } else {
                volume += candle.volume
            }
        }
        return volume
    }

    fun getTodayVolume(): Int {
        return candleToday?.volume ?: 0
    }

    fun getPriceNow(): Double {
        var value = 0.0

        closePrices?.let {
            value = it.post
        }

        candleToday?.let {
            value = it.closingPrice
        }

        if (minuteCandles.isNotEmpty()) {
            value = minuteCandles.last().closingPrice
        }

        return value
    }

    fun getPrice0300(): Double {
        return closePrices?.yahoo ?: 0.0
    }

    fun getPrice2300(): Double {
        return closePrices?.os ?: 0.0
    }

    fun getPrice1000(): Double {
        return candleToday?.openingPrice ?: getPrice0145()
    }

    fun getPrice0145(): Double {
        return closePrices?.post ?: 0.0
    }

    fun getPriceString(): String {
        return getPriceNow().toMoney(this)
    }

    fun getPrice2359String(): String {
        closePrices?.let {
            return it.os.toMoney(this)
        }
        return "0$"
    }

    private fun updateChangeToday() {
        candleToday?.let { candle ->
            closePrices?.let { close ->
                middlePrice = (candle.highestPrice + candle.lowestPrice) / 2.0
                dayVolumeCash = middlePrice * candle.volume
            }
        }
    }

    fun updateChange2300() {
        closePrices?.let {
            changePrice2300DayAbsolute = it.post - it.os
            changePrice2300DayPercent = (100 * it.post) / it.os - 100

            candleToday?.let { today ->
                changePrice2300DayAbsolute = today.closingPrice - it.os
                changePrice2300DayPercent = (100 * today.closingPrice) / it.os - 100
            }
        }
    }

    private fun updateChangeFixPrice() {
        if (needToFixPrice && priceFixed == 0.0) {
            priceFixed = getPriceNow()
            needToFixPrice = false
        }
        val currentPrice = getPriceNow()
        changePriceFixDayAbsolute = currentPrice - priceFixed
        changePriceFixDayPercent = currentPrice / priceFixed * 100.0 - 100.0
    }

    fun resetFixPrice() {
        needToFixPrice = true
        changePriceFixDayAbsolute = 0.0
        changePriceFixDayPercent = 0.0
        priceFixed = getPriceNow()
        if (minuteCandles.isNotEmpty()) {
            minuteCandleFixed = minuteCandles.last()
        }
    }
}