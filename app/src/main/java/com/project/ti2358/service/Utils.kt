package com.project.ti2358.service

import android.app.*
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.navigation.NavController
import com.project.ti2358.BuildConfig
import com.project.ti2358.MainActivity
import com.project.ti2358.R
import com.project.ti2358.TheApplication
import com.project.ti2358.data.common.BrokerType
import com.project.ti2358.data.manager.*
import com.project.ti2358.data.tinkoff.model.Currency
import com.project.ti2358.data.tinkoff.model.Interval
import com.project.ti2358.data.tinkoff.model.OperationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

fun log(msg: String) {
    if (BuildConfig.DEBUG) {
        Log.d("ti2358-service", msg)
    }
}

enum class Sorting {
    ASCENDING,
    DESCENDING
}

enum class Step1728 {
    step700to1200,
    step700to1530,
    step1630to1635,
    stepFinal,
}

enum class ScreenerType {
    screener2300,
    screener0145,
    screener0300,
    screener0700,
    screenerNow,
}

enum class PurchaseStatus {
    NONE,
    ORDER_BUY_PREPARE,
    ORDER_BUY,
    BOUGHT,
    ORDER_SELL_TRAILING,
    ORDER_SELL_PREPARE,
    ORDER_SELL,
    WAITING,
    SOLD,
    CANCELED,
    PART_FILLED,

    ERROR_NEED_WATCH,
}

enum class LimitType {
    ON_UP,
    ON_DOWN,

    NEAR_UP,
    NEAR_DOWN,

    ABOVE_UP,
    UNDER_DOWN
}

fun Date.toString(format: String, locale: Locale = Locale.getDefault()): String {
    val formatter = SimpleDateFormat(format, locale)
    return formatter.format(this)
}

fun Double.toMoney(stock: Stock?, showSymbol: Boolean = true): String {
    var symbol = stock?.getCurrencySymbol() ?: "$"
    if (!showSymbol) {
        symbol = ""
    }
    if (stock?.instrument?.currency == Currency.RUB) {
        if (stock.instrument.minPriceIncrement == 0.01) {
            return "%.2f%s".format(locale = Locale.US, this, symbol)
        } else {
            return "%.4f%s".format(locale = Locale.US, this, symbol)
        }
    } else {
        return "%.2f%s".format(locale = Locale.US, this, symbol)
    }
}

fun Double.toPercent(): String {
    return "%.2f%%".format(locale = Locale.US, this)
}

class Utils{
    companion object {
        val DARK_BLUE: Int = Color.parseColor("#1A134C")

        val GREEN: Int = Color.parseColor("#58D68D")
        val RED: Int = Color.parseColor("#E74C3C")

        val BLACK: Int = Color.parseColor("#000000")
        val WHITE: Int = Color.parseColor("#FFFFFF")

        val LIGHT: Int = Color.parseColor("#35888888")
        val WHITE_NIGHT: Int = Color.parseColor("#05FFFFFF")
        val WHITE_DAY: Int = Color.parseColor("#15888888")
        val EMPTY: Int = Color.parseColor("#00FFFFFF")
        val PURPLE: Int = Color.parseColor("#C400AB")

        val PRINT_RED: Int = Color.parseColor("#e16f8e")
        val PRINT_BLUE: Int = Color.parseColor("#4f8fc4")

        val TEAL: Int = Color.parseColor("#4903DAC5")

        val TINKOFF_CLEAR: Int = Color.parseColor("#33f1d62c")
        val ALOR_CLEAR: Int = Color.parseColor("#330F59a9")

        val TINKOFF: Int = Color.parseColor("#77f1d62c")
        val ALOR: Int = Color.parseColor("#770F59a9")

        val TINKOFF_BRIGHT: Int = Color.parseColor("#FFf1d62c")
        val ALOR_BRIGHT: Int = Color.parseColor("#FF0F59a9")

        val TRANSPARENT: Int = Color.parseColor("#000F59a9")

        @KoinApiExtension
        fun getColorForBrokerValue(brokerType: BrokerType, clear: Boolean = false): Int {
            if (brokerType == BrokerType.TINKOFF) return if (clear) TINKOFF_CLEAR else TINKOFF
            if (brokerType == BrokerType.ALOR) return if (clear) ALOR_CLEAR else ALOR
            return TRANSPARENT
        }

        @KoinApiExtension
        fun getNeutralColor(): Int {
            return if (SettingsManager.getDarkTheme()) WHITE else BLACK
        }

        @KoinApiExtension
        fun getColorForValue(value: Double, black: Boolean = true): Int {
            if (value > 0) return GREEN
            if (value < 0) return RED
            return if (black) getNeutralColor() else WHITE
        }

        @KoinApiExtension
        fun getColorForOperation(value: OperationType): Int {
            if (value == OperationType.BUY) return RED
            if (value == OperationType.SELL) return GREEN
            return getNeutralColor()
        }

        fun getColorForIndex(index: Int): Int {
            when (TheApplication.application.resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK)) {
                Configuration.UI_MODE_NIGHT_YES -> {
                    return if (index % 2 == 0) WHITE_NIGHT else EMPTY
                }
                Configuration.UI_MODE_NIGHT_NO -> {
                    return if (index % 2 == 0) WHITE_DAY else EMPTY
                }
                Configuration.UI_MODE_NIGHT_UNDEFINED -> {}
            }
            return EMPTY
        }

        fun getColorBackgroundForPrint(hit: Int): Int {
            return when (hit) {
                0 -> Color.TRANSPARENT
                1 -> PRINT_RED
                2 -> PRINT_BLUE
                3 -> PRINT_BLUE
                4 -> PRINT_RED
                5 -> Color.TRANSPARENT
                else -> Color.TRANSPARENT
            }
        }

        @KoinApiExtension
        fun getColorTextForPrint(hit: Int): Int {
            return when (hit) {
                0 -> getNeutralColor()
                1 -> getNeutralColor()
                2 -> getNeutralColor()
                3 -> Color.CYAN
                4 -> Color.CYAN
                5 -> getNeutralColor()
                else -> getNeutralColor()
            }
        }

        fun getColorForSector(sector: String?): Int {
            return when (sector) {
                "HealthCare" -> Color.parseColor("#d9bc3f")
                "Consumer" -> Color.parseColor("#9d5451")
                "IT" -> Color.parseColor("#19cabf")
                "Industrials" -> Color.parseColor("#af824f")
                "Telecom" -> Color.parseColor("#d1916a")
                "Materials" -> Color.parseColor("#a469af")
                "Financial" -> Color.parseColor("#3ca4f5")
                "Energy" -> Color.parseColor("#bc8fc0")
                "Utilities" -> Color.parseColor("#52a35e")
                else -> Color.parseColor("#E74C3C")
            }
        }

        fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
            manager?.let {
                for (service in it.getRunningServices(Int.MAX_VALUE)) {
                    if (serviceClass.name == service.service.className) {
                        return true
                    }
                }
            }
            return false
        }

        fun startService(context: Context, serviceClass: Class<*>) {
            Intent(context, serviceClass).also {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(it)
                } else {
                    context.startService(it)
                }
            }
        }

        fun stopService(context: Context, serviceClass: Class<*>) {
            context.stopService(Intent(context, serviceClass))
        }

        fun showToastAlert(text: String) {
            GlobalScope.launch(Dispatchers.Main) {
                try {
                    Toast.makeText(TheApplication.application.applicationContext, text, LENGTH_SHORT).show()
                } catch (e: Exception) {

                }
            }
        }

        fun showMessageAlert(context: Context, text: String) {
            val alertDialogBuilder = AlertDialog.Builder(context)
            val alertDialog = alertDialogBuilder.create()
            alertDialog.setMessage(text)
            alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "понятно") { dialog, _ -> dialog.cancel() }
            alertDialog.show()
        }

        fun showErrorAlert(context: Context) {
            val alertDialogBuilder = AlertDialog.Builder(context)
            val alertDialog = alertDialogBuilder.create()
            alertDialog.setTitle("Ошибка")
            alertDialog.setMessage("Нужно выбрать хотя бы 1 бумагу")
            alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "ой, всё") { dialog, _ -> dialog.cancel() }
            alertDialog.show()
        }

        fun showUpdateAlert(context: Context, from: String, to: String) {
            val alertDialogBuilder = AlertDialog.Builder(context)
            val alertDialog = alertDialogBuilder.create()
            alertDialog.setTitle("Новая версия! 🔥")
            alertDialog.setMessage("Обновляемся? \n$from >>> $to")
            alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "ДА!") { dialog, _ ->
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/oostap/2358/releases/latest"))
                context.startActivity(browserIntent)
                dialog.cancel() }
            alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "НЕТ.") { dialog, _ -> dialog.cancel() }
            alertDialog.show()
        }

        @KoinApiExtension
        fun openOrderbookForStock(navController: NavController, orderbookManager: OrderbookManager, stock: Stock) {
            orderbookManager.start(stock)
            navController.navigate(R.id.nav_orderbook)
        }

        @KoinApiExtension
        fun openChartForStock(navController: NavController, chartManager: ChartManager, stock: Stock) {
            chartManager.start(stock)
            navController.navigate(R.id.nav_chart)
        }

        fun openTinkoffForTicker(context: Context, ticker: String) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tinkoff.ru/invest/stocks/$ticker/"))
            context.startActivity(browserIntent)
        }

        fun getTimeMSK(): Calendar {
            return Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow"))
        }

        fun getTimeUTC(): Calendar {
            return Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        }

        fun getTimeDiffBetweenMSK_UTC(): Int {
            val now = getTimeMSK()
            val utc = getTimeUTC()
            val nowOffs = now.timeZone.rawOffset
            val utcOffs = utc.timeZone.rawOffset
            return ((nowOffs - utcOffs) / 1000 / 3600.0).toInt()
        }

        fun getTimeDiffBetweenMSK(): Int {
            val now = Calendar.getInstance(TimeZone.getDefault())
            val msk = getTimeMSK()
            val nowOffs = now.timeZone.rawOffset
            val moscowOffs = msk.timeZone.rawOffset
            return ((nowOffs - moscowOffs) / 1000 / 3600.0).toInt()
        }

        fun isNight(): Boolean {
            val msk = getTimeMSK()
            val hour = msk.get(Calendar.HOUR_OF_DAY)
            val minute = msk.get(Calendar.MINUTE)

            if (hour == 6 && minute > 55) {
                return false
            }

            if (2 <= hour && hour < 7) {
                return true
            }

            log("MSK: $hour:$minute")
            return false
        }

        fun isHighSpeedSession(): Boolean {
            val msk = getTimeMSK()
            val hour = msk.get(Calendar.HOUR_OF_DAY)
            val minute = msk.get(Calendar.MINUTE)

            if (hour == 6 && minute > 58) {
                return true
            }

            if (hour == 7 && minute < 10) {
                return true
            }

            if (hour == 9 && minute > 58) {
                return true
            }

            if (hour == 10 && minute < 10) {
                return true
            }

            if (hour == 16 && minute > 55) {
                return true
            }

            if (hour == 17 && minute < 20) {
                return true
            }

            if (hour == 23 && minute > 55) {
                return true
            }

            if (hour == 0 && minute < 5) {
                return true
            }

            return false
        }

        fun isActiveSession(): Boolean {
            val msk = getTimeMSK()
            val hour = msk.get(Calendar.HOUR_OF_DAY)

            if (hour >= 7 || hour <= 2) {
                return true
            }

            return false
        }

        fun isMorningSession(): Boolean {
            val msk = getTimeMSK()
            val hour = msk.get(Calendar.HOUR_OF_DAY)
            return hour in 7..9
        }

        fun isSessionBefore11(): Boolean {
            val msk = getTimeMSK()
            val hour = msk.get(Calendar.HOUR_OF_DAY)

            if (hour < 11 && hour >= 7) {
                return true
            }

            return false
        }

        @KoinApiExtension
        fun createNotification(
            context: Context,
            channelId: String,
            summaryTitle: String,
            shortText: String,
            longText: String,
            longTitleText: String,
            action1: Notification.Action? = null,
            action2: Notification.Action? = null,
            action3: Notification.Action? = null,
            action4: Notification.Action? = null,
        ): Notification {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = NotificationChannel(
                    channelId,
                    "2358 notifications channel",
                    NotificationManager.IMPORTANCE_HIGH
                ).let {
                    it.description = channelId
                    it.lightColor = Color.RED
                    it.enableVibration(false)
                    it
                }
                notificationManager.createNotificationChannel(channel)
            }

            val pendingIntent: PendingIntent = Intent(context, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(context, 0, notificationIntent, 0)
            }

            val builder: Notification.Builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, channelId) else Notification.Builder(context)

            builder
                .setContentText(shortText)
                .setStyle(Notification.BigTextStyle().setSummaryText(summaryTitle).bigText(longText).setBigContentTitle(longTitleText))
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOnlyAlertOnce(true)
                .setOngoing(false)

            if (action1 != null) {
                builder.addAction(action1)
            }

            if (action2 != null) {
                builder.addAction(action2)
            }

            if (action3 != null) {
                builder.addAction(action3)
            }

            if (action4 != null) {
                builder.addAction(action4)
            }

            return builder.build()
        }

        fun makeNicePrice(price: Double, stock: Stock?): Double {
            if (price.isNaN()) return 0.0

            if (stock == null || stock.instrument.minPriceIncrement == 0.01) {
                return (price * 100.0).roundToInt() / 100.0
            }

            val minIncrement = stock.instrument.minPriceIncrement

            if (minIncrement == 0.0) return 0.0
            if ((price / minIncrement).isNaN()) return 0.0

            val mult = (price / minIncrement).roundToInt()
            return mult * minIncrement
        }

        fun getTimezoneCurrent(): String {
            val tz = TimeZone.getDefault()
            val cal = Calendar.getInstance(tz)
            val offsetInMillis = tz.getOffset(cal.timeInMillis)
            var offset = String.format("%02d:%02d", abs(offsetInMillis / 3600000), abs(offsetInMillis / 60000 % 60))
            offset = (if (offsetInMillis >= 0) "+" else "-") + offset
            return offset
        }

        fun convertIntervalToAlorTimeframe(interval: Interval): Any {
            when (interval) {
                Interval.MINUTE -> {
                    return 60
                }
                Interval.HOUR -> {
                    return 60 * 60
                }
                Interval.TWO_HOURS -> {
                    return 60 * 60 * 2
                }
                Interval.DAY -> {
                    return "D"
                }
                Interval.WEEK -> {
                    return 60 * 60 * 24 * 7
                }
            }

            return 60
        }

        fun convertIntervalToSeconds(interval: Interval): Int {
            return when (interval) {
                Interval.MINUTE -> 60
                Interval.HOUR -> 60 * 60
                Interval.TWO_HOURS -> 60 * 60 * 2
                Interval.DAY -> 60 * 60 * 24
                Interval.WEEK -> 60 * 60 * 24 * 7
                Interval.TWO_MINUTES -> 60 * 2
                Interval.THREE_MINUTES -> 60 * 3
                Interval.FIVE_MINUTES -> 60 * 5
                Interval.TEN_MINUTES -> 60 * 10
                Interval.FIFTEEN_MINUTES -> 60 * 15
                Interval.THIRTY_MINUTES -> 60 * 30
                Interval.FOUR_HOURS -> 60 * 60 * 5
                Interval.MONTH -> 60 * 60 * 24 * 30
            }
        }

        fun convertIntervalToString(interval: Interval): String {
            return when (interval) {
                Interval.MINUTE -> "1min"
                Interval.HOUR -> "hour"
                Interval.TWO_HOURS -> "2hour"
                Interval.DAY -> "day"
                Interval.WEEK -> "week"
                Interval.TWO_MINUTES -> "2min"
                Interval.THREE_MINUTES -> "3min"
                Interval.FIVE_MINUTES -> "5min"
                Interval.TEN_MINUTES -> "10min"
                Interval.FIFTEEN_MINUTES -> "15min"
                Interval.THIRTY_MINUTES -> "30min"
                Interval.FOUR_HOURS -> "4hour"
                Interval.MONTH -> "month"
            }
        }

        fun convertStringToInterval(interval: String): Interval? {
            when (interval) {
                "1min" -> { return Interval.MINUTE }
                "hour" -> { return Interval.HOUR }
                "2hour" -> { return Interval.TWO_HOURS }
                "day" -> { return Interval.DAY }
                "week" -> { return Interval.WEEK }
            }

            return null
        }

        @KoinApiExtension
        @JvmName("searchStocks")
        fun search(stockPurchases: List<StockPurchase>, text: String): MutableList<StockPurchase> {
            if (text.isNotEmpty()) {
                val list = stockPurchases.filter {
                    it.ticker.contains(text, ignoreCase = true) ||
                            it.stock.instrument.name.contains(text, ignoreCase = true) ||
                            it.stock.alterName.contains(text, ignoreCase = true)
                }.toMutableList()
                list.sortBy { it.ticker.length }
                return list
            }
            return stockPurchases.toMutableList()
        }

        fun search(stocks: List<Stock>, text: String): MutableList<Stock> {
            if (text.isNotEmpty()) {
                val list = stocks.filter {
                            it.ticker.contains(text, ignoreCase = true) ||
                            it.instrument.name.contains(text, ignoreCase = true) ||
                            it.alterName.contains(text, ignoreCase = true)
                }.toMutableList()
                list.sortBy { it.ticker.length }
                return list
            }
            return stocks.toMutableList()
        }

        fun getPercentFromTo(from: Double, to: Double): Double {
            return from / to * 100.0 - 100.0
        }

        fun getEmojiForPercent(percent: Double): String {
            return when {
                percent <= -20 -> " 💩"
                percent <= -15 -> " 🦌"
                percent <= -10 -> " 🤬"
                percent <= -5 -> " 😡"
                percent <= -3 -> " 😱"
                percent <= -1 -> " 😰"
                percent >= 20 -> " 🤪️"
                percent >= 15 -> " ❤️"
                percent >= 10 -> " 🤩"
                percent >= 5 -> " 😍"
                percent >= 3 -> " 🥳"
                percent >= 1 -> " 🤑"
                else -> ""
            }
        }

        fun getEmojiSuperIndex(percent: Double): String {
            return when {
                percent >= 5.0 -> "😎😎😎"
                percent >= 4.0 -> "🤡🤡🤡"
                percent >= 3.0 -> "🥳🤪🤩"
                percent >= 2.0 -> "😍🤑😇"
                percent >= 1.0 -> "😍🤑"
                percent >= 0.2 -> "🥰"
                abs(percent) < 0.2 -> "😐"
                percent <= -6 -> "💩💩💩"
                percent <= -5 -> "☠️☠️☠️"
                percent <= -4 -> "🥵🤬😡️"
                percent <= -3 -> "👿🤢😤️"
                percent <= -2 -> "😦😨😣"
                percent <= -1 -> "😰😭"
                percent <= -0.2 -> "😧"
                else -> ""
            }
        }

        fun getUSDRUB(): Double {
            return 74.0
        }
    }
}