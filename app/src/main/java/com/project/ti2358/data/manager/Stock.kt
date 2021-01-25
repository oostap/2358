package com.project.ti2358.data.service

import com.project.ti2358.data.model.dto.Candle
import com.project.ti2358.data.model.dto.Interval
import com.project.ti2358.data.model.dto.MarketInstrument
import com.project.ti2358.service.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random.Default.nextInt

@KoinApiExtension
data class Stock(
    var marketInstrument: MarketInstrument
) : KoinComponent {
    private val marketService: MarketService by inject()

    var changePriceDayAbsolute: Double = 0.0
    var changePriceDayPercent: Double = 0.0
    var middlePrice: Double = 0.0
    var dayVolumeCash: Double = 0.0

    var priceNow: Double = 0.0

    var changeOnStartTimer: Double = 0.0

    public var todayDayCandle: Candle = Candle()
    public var lastWeekDayCandles: List<Candle> = emptyList()

    public var minuteCandles: MutableList<Candle> = mutableListOf()

    public fun processCandle(candle: Candle) {
        if (candle.interval == Interval.DAY) {
            processDayCandle(candle)
        } else if (candle.interval == Interval.MINUTE) {
            processMinuteCandle(candle)
        }
    }

    public fun processDayCandle(candle: Candle) {
        val diffInMilli: Long = Calendar.getInstance().time.time - candle.time.time
        val diffInDays: Long = TimeUnit.MILLISECONDS.toDays(diffInMilli)
        if (diffInDays > 1) return

        todayDayCandle = candle

        changePriceDayAbsolute = todayDayCandle.closingPrice - todayDayCandle.openingPrice
        changePriceDayPercent = (100 * todayDayCandle.closingPrice) / todayDayCandle.openingPrice - 100

        middlePrice = (todayDayCandle.highestPrice + todayDayCandle.lowestPrice ) / 2.0
        dayVolumeCash = middlePrice * todayDayCandle.volume

        priceNow = todayDayCandle.closingPrice
    }

    public fun processMinuteCandle(candle: Candle) {
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

//        log("minuteCandles.size = ${minuteCandles.size}")
    }

    public fun getPriceString(): String {
        return "${todayDayCandle.closingPrice} $"
    }

    companion object {
        var a : Int = 0
    }

    public fun loadLastWeekDayCandles(prevDelay: Long): Long {
        return prevDelay
//         TODO: ! unused yet
//        val delay = prevDelay + kotlin.random.Random.Default.nextLong(200, 500)
//        GlobalScope.launch(Dispatchers.Main) {
//            while (lastWeekDayCandles.isEmpty()) {
//                try {
//                    delay(delay)
//                    val zone = getCurrentTimezoneOffset()
//                    val now = getDateTimeCurrent() + zone
//                    val weekAgo = getDateTimeWeekAgo() + zone
//
//                    log(now + zone)
//                    log(weekAgo + zone)
//
//                    val candles = marketService.candles(
//                        marketInstrument.figi,
//                        Interval.DAY,
//                        weekAgo,
//                        now
//                    )
//                    lastWeekDayCandles = candles.candles
//
//                    log(marketInstrument.ticker + " = " + candles)
//                    log("TOTAL = ${a++}")
//
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                }
//            }
//        }
//        return delay
    }

    fun Date.toString(format: String, locale: Locale = Locale.getDefault()): String {
        val formatter = SimpleDateFormat(format, locale)
        return formatter.format(this)
    }

    fun getDateTimeCurrent(): String {
        return Calendar.getInstance().time.toString("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    }

    fun getDateTimeWeekAgo(): String {
        var calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -5)
        return calendar.time.toString("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    }

    fun getCurrentTimezoneOffset(): String? {
        val tz = TimeZone.getDefault()
        val cal = GregorianCalendar.getInstance(tz)
        val offsetInMillis = tz.getOffset(cal.timeInMillis)
        var offset = String.format(
            "%02d:%02d", Math.abs(offsetInMillis / 3600000), Math.abs(
                offsetInMillis / 60000 % 60
            )
        )
        offset = (if (offsetInMillis >= 0) "+" else "-") + offset
        return offset
    }
}