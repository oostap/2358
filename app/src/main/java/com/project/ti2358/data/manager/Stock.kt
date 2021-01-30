package com.project.ti2358.data.service

import android.R
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.GsonBuilder
import com.project.ti2358.data.model.dto.Candle
import com.project.ti2358.data.model.dto.Interval
import com.project.ti2358.data.model.dto.MarketInstrument
import com.project.ti2358.service.Utils
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


@KoinApiExtension
data class Stock(
    var marketInstrument: MarketInstrument
) : KoinComponent {
    private val marketService: MarketService by inject()
    private val stockManager: StockManager by inject()

    var changePriceDayAbsolute: Double = 0.0
    var changePriceDayPercent: Double = 0.0
    var middlePrice: Double = 0.0
    var dayVolumeCash: Double = 0.0

    var priceNow: Double = 0.0

    var changeOnStartTimer: Double = 0.0

    var todayDayCandle: Candle = Candle()
    var yesterdayClosingCandle: Candle? = null
    var changePriceFromClosingDayAbsolute: Double = 0.0
    var changePriceFromClosingDayPercent: Double = 0.0

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

//        stockManager.unsubscribeStock(this, Interval.DAY)
        loadClosingPriceDelay = loadClosingPriceCandle(loadClosingPriceDelay)

        updateChangeFromClosing()
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
        return "${todayDayCandle.closingPrice}$"
    }

    public fun getClosingPriceString(): String {
        if (yesterdayClosingCandle != null) {
            return "${yesterdayClosingCandle?.closingPrice}$"
        }
        return "0$"
    }

    companion object {
        var loadClosingPriceDelay: Long = 0
    }

//    public fun weeeeeek(prevDelay: Long): Long {
//        if (yesterdayClosingCandle != null) return prevDelay
//
//        val delay = prevDelay + kotlin.random.Random.Default.nextLong(300, 600)
//        GlobalScope.launch(Dispatchers.Main) {
//            while (yesterdayClosingCandle == null) {
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
////                    log("TOTAL = ${a++}")
//
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                }
//            }
//        }
//        return delay
//    }

    private fun updateChangeFromClosing() {
        yesterdayClosingCandle?.let {
            changePriceFromClosingDayAbsolute = todayDayCandle.closingPrice - it.openingPrice
            changePriceFromClosingDayPercent = (100 * todayDayCandle.closingPrice) / it.openingPrice - 100
        }
    }

    public fun loadClosingPriceCandle(prevDelay: Long): Long {
        if (yesterdayClosingCandle != null) return prevDelay

        val gson = GsonBuilder().create()

        val zone = getCurrentTimezone()
        val from = getLastClosingDate(true) + zone

        val key = "closing_${marketInstrument.figi}_${from}"

        val preferences = PreferenceManager.getDefaultSharedPreferences(SettingsManager.context)
        val jsonClosingCandle = preferences.getString(key, null)
        if (jsonClosingCandle != null) {
            yesterdayClosingCandle = gson.fromJson(jsonClosingCandle, Candle::class.java)
        }

        val delay = prevDelay + kotlin.random.Random.Default.nextLong(300, 600)
        GlobalScope.launch(Dispatchers.Main) {
            while (yesterdayClosingCandle == null) {
                try {
                    delay(delay)
                    val to = getLastClosingDate(false) + zone

                    log(marketInstrument.ticker + " " + marketInstrument.figi)
                    log(from)
                    log(to)

                    val candles = marketService.candles(
                        marketInstrument.figi,
                        "1min",
                        from,
                        to
                    )

                    if (candles.candles.isNotEmpty()) {
                        yesterdayClosingCandle = candles.candles[0]
                        val data = gson.toJson(yesterdayClosingCandle)

                        val editor: SharedPreferences.Editor = preferences.edit()
                        editor.putString(key, data)
                        editor.apply()

                        updateChangeFromClosing()
                    }

                    log(marketInstrument.ticker + " = " + candles)
                    break
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return delay
    }

    fun getLastClosingDate(before: Boolean): String {
        val differenceHours: Int = Utils.getTimeDiffBetweenMSK()

        var hours = 23
        var minutes = 59
        var seconds = 0
        var deltaDay = -1

        if (!before) {
            hours = 0
            minutes = 0
            seconds = 0
            deltaDay = 0
        }

        var time = Calendar.getInstance(TimeZone.getDefault())
        time.add(Calendar.HOUR_OF_DAY, -differenceHours)
        time.set(Calendar.HOUR_OF_DAY, hours)
        time.set(Calendar.MINUTE, minutes)
        time.set(Calendar.SECOND, seconds)
        time.set(Calendar.MILLISECOND, 0)
        time.add(Calendar.HOUR_OF_DAY, differenceHours)
        time.add(Calendar.DAY_OF_MONTH, deltaDay)

        // если воскресенье, то откатиться к субботе
        if (time.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            time.add(Calendar.DAY_OF_MONTH, -1)
        }

        // если понедельник, то откатиться к субботе
        if (time.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) {
            time.add(Calendar.DAY_OF_MONTH, -2)
        }

        return time.time.toString("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
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

    fun getCurrentTimezone(): String? {
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