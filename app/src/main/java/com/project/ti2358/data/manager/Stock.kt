package com.project.ti2358.data.manager

import com.project.ti2358.data.daager.model.*
import com.project.ti2358.data.pantini.model.PantiniLenta
import com.project.ti2358.data.pantini.model.PantiniOrderbook
import com.project.ti2358.data.pantini.model.PantiniPrint
import com.project.ti2358.data.tinkoff.model.*
import com.project.ti2358.data.tinkoff.model.Currency
import com.project.ti2358.service.ScreenerType
import com.project.ti2358.service.Utils
import com.project.ti2358.service.toMoney
import org.koin.core.component.KoinApiExtension
import java.util.*
import java.util.concurrent.TimeUnit

data class Stock(var instrument: Instrument) {
    var ticker = instrument.ticker
    var figi = instrument.figi

    var alterName: String = ""

    var report: Report? = null      // отчёт
    var dividend: Dividend? = null  // дивы
    var fda: FDA? = null            // fda фаза

    var short: StockShort? = null
    var stockIndices: Map<String, Double>? = null
    var morning: Any? = null

    var stockInfo: InstrumentInfo? = null           // инфа
    var orderbookStream: OrderbookStream? = null    // стакан
    var orderbookUS: PantiniOrderbook? = null       // стакан US
    var lentaUS: PantiniLenta? = null               // лента принтов

    var dayVolumeCash: Double = 0.0

    var changeOnStartTimer: Double = 0.0    // сколько % было на старте таймера для 2358

    var closePrices: ClosePrice? = null
    var candleToday: Candle? = null                               // реалтайм, дневная свеча
    var minutesVolume: Int = 0

    // разница с ценой low
    var lowPrice: Double = 0.0
    var changePriceLowDayAbsolute: Double = 0.0
    var changePriceLowDayPercent: Double = 0.0

    // разница с ценой high
    var highPrice: Double = 0.0
    var changePriceHighDayAbsolute: Double = 0.0
    var changePriceHighDayPercent: Double = 0.0

    // арбитраж по стакану
    var askPriceRU: Double = 0.0
    var askLotsRU: Int = 0
    var changePriceArbLongPercent: Double = 0.0
    var changePriceArbLongAbsolute: Double = 0.0

    var bidPriceRU: Double = 0.0
    var bidLotsRU: Int = 0
    var changePriceArbShortPercent: Double = 0.0
    var changePriceArbShortAbsolute: Double = 0.0

    // разница с ценой закрытия ОС
    var changePrice2300DayAbsolute: Double = 0.0
    var changePrice2300DayPercent: Double = 0.0

    // для Trends
    var priceTrend: Double = 0.0
    var trendStartTime: Calendar = Calendar.getInstance()

    // разница со старта таймера для FixPrice
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
    var changePrice1628to1635Absolute: Double = 0.0
    var changePrice1628to1635Percent: Double = 0.0
    var volume1628to1635 = 0
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
        if (StrategyLove.stocksSelected.find { it.ticker == ticker } != null) {
            t += "❤️"
        }

        if (StrategyBlacklist.stocksSelected.find { it.ticker == ticker } != null) {
            t += "🖤️️"
        }

        if (morning != null) {
            t += "🕖"
        }

        if (short != null) {
            t += "👖"
        }
        if (report != null) {
            t += "️📊"
        }
        if (dividend != null) {
            t += "💰"
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

    fun processSector() {
        val sector = closePrices?.sector ?: ""
        if (sector == "Industrials") {
            closePrices?.sector = "Industrial"
        }

        if (sector == "Healthcare") {
            closePrices?.sector = "HealthCare"
        }
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

        if (orderbookStream != null) {
            val priceClose = closePrices?.os ?: 0.0
            if (orderbookStream?.asks?.size!! > 0) {
                askPriceRU = orderbookStream?.asks?.first()?.get(0) ?: 0.0
                askLotsRU = orderbookStream?.asks?.first()?.get(1)?.toInt() ?: 0
            }

            if (orderbookStream?.bids?.size!! > 0) {
                bidPriceRU = orderbookStream?.bids?.first()?.get(0) ?: 0.0
                bidLotsRU = orderbookStream?.bids?.first()?.get(1)?.toInt() ?: 0
            }

            changePriceArbLongPercent = priceClose / askPriceRU * 100.0 - 100.0
            changePriceArbLongAbsolute = priceClose - askPriceRU

            changePriceArbShortPercent = bidPriceRU / priceClose * 100.0 - 100.0
            changePriceArbShortAbsolute = bidPriceRU - priceClose
        }
    }

    fun processOrderbookUS(pantiniOrderbook: PantiniOrderbook) {
        orderbookUS = pantiniOrderbook
    }

    fun processLentaUS(pantiniLenta: PantiniLenta) {
        if (lentaUS == null) {
            lentaUS = PantiniLenta(ticker)
        }

        lentaUS?.prints?.addAll(0, pantiniLenta.prints)
        lentaUS?.prints = (lentaUS?.prints?.subList(0, 100) ?: emptyList()) as MutableList<PantiniPrint>
    }

    fun processStockInfo(instrumentInfo: InstrumentInfo) {
        stockInfo = instrumentInfo
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
        updateChangeFixPrice()
    }

    private fun processHour1Candle(candle: Candle) {
        // do nothing
    }

    private fun processHour2Candle(candle: Candle) {
        // do nothing
    }

    @Synchronized
    private fun processMinuteCandle(candle: Candle) {
        var exists = false
        synchronized(minuteCandles) {
            minutesVolume = 0
            for ((index, c) in minuteCandles.withIndex()) {
                if (c.time == candle.time) {
                    minuteCandles[index] = candle
                    exists = true
                }
                minutesVolume += minuteCandles[index].volume
            }
            if (!exists) {
                minuteCandles.add(candle)
            }

            minuteCandles.sortBy { it.time }
        }

        updateChangeToday()
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
        return minutesVolume + (candleToday?.volume ?: 0)
    }

    fun getPriceNow(): Double {
        var value = 0.0

        val hour = Utils.getTimeMSK().get(Calendar.HOUR_OF_DAY)
        if (hour > 2 && hour < 7) { // если время до старта, то всегда берём post
            closePrices?.let {
                value = it.post
            }
        } else if (hour >= 7 && hour < 10 && morning == null) { // если с 7 до 10 и бумага не торгуется с утра, то берём post
            closePrices?.let {
                value = it.post
            }
        } else {
            closePrices?.let {
                value = it.post
            }

            candleToday?.let {
                value = it.closingPrice
            }

            if (minuteCandles.isNotEmpty()) {
                value = minuteCandles.last().closingPrice
            }
        }

        return value * instrument.lot
    }

    fun getPrice0300(): Double {
        return closePrices?.yahoo ?: 0.0
    }

    fun getPrice2300(): Double {
        if (instrument.currency == Currency.USD) {
            return closePrices?.os ?: 0.0
        } else {
            return getPrice1000() * instrument.lot
        }
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
        return getPrice2300().toMoney(this)
    }

    fun updateChangeToday() {
        candleToday?.let { candle ->
            val middlePrice = (candle.highestPrice + candle.lowestPrice) / 2.0
            dayVolumeCash = middlePrice * getTodayVolume()
        }

        changePrice2300DayAbsolute = getPriceRaw() - getPrice2300()
        changePrice2300DayPercent = (100.0 * getPriceRaw()) / getPrice2300() - 100.0

        var low = candleToday?.lowestPrice ?: getPriceRaw()
        for (candle in minuteCandles) {
            if (candle.lowestPrice < low) {
                low = candle.lowestPrice
            }
        }
        lowPrice = low
        changePriceLowDayAbsolute = getPriceRaw() - lowPrice
        changePriceLowDayPercent = (100.0 * getPriceRaw()) / lowPrice - 100.0

        var high = candleToday?.highestPrice ?: getPriceRaw()
        for (candle in minuteCandles) {
            if (candle.highestPrice > high) {
                high = candle.highestPrice
            }
        }
        highPrice = high
        changePriceHighDayAbsolute = getPriceRaw() - highPrice
        changePriceHighDayPercent = (100.0 * getPriceRaw()) / highPrice - 100.0

//        log("$ticker low=$lowPrice=$changePriceLowDayAbsolute / $changePriceLowDayPercent, high=$highPrice=$changePriceHighDayAbsolute / $changePriceHighDayPercent")
    }

    fun getPriceRaw(): Double {
        if (minuteCandles.isNotEmpty()) {
            return minuteCandles.last().closingPrice
        }

        if (candleToday != null) {
            return candleToday?.closingPrice ?: 0.0
        }

        if (closePrices != null) {
            return closePrices?.post ?: 0.0
        }

        return 0.0
    }

    private fun updateChangeFixPrice() {
        if (priceFixed == 0.0) priceFixed = getPriceRaw()
        val currentPrice = getPriceRaw()
        changePriceFixDayAbsolute = currentPrice - priceFixed
        changePriceFixDayPercent = currentPrice / priceFixed * 100.0 - 100.0
    }

    fun resetFixPrice() {
        changePriceFixDayAbsolute = 0.0
        changePriceFixDayPercent = 0.0
        priceFixed = getPriceRaw()
        if (minuteCandles.isNotEmpty()) {
            minuteCandleFixed = minuteCandles.last()
        }
    }

    fun resetTrendPrice() {
        priceTrend = getPriceRaw()
        trendStartTime = Calendar.getInstance()
        trendStartTime.set(Calendar.SECOND, 0)
    }
}