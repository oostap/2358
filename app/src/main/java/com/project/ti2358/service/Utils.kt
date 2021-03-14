package com.project.ti2358.service

import android.app.*
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import com.project.ti2358.BuildConfig
import com.project.ti2358.MainActivity
import com.project.ti2358.R
import com.project.ti2358.TheApplication
import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.model.dto.Interval
import org.koin.core.component.KoinApiExtension
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

fun Date.toString(format: String, locale: Locale = Locale.getDefault()): String {
    val formatter = SimpleDateFormat(format, locale)
    return formatter.format(this)
}

fun Double.toMoney(stock: Stock?): String {
    val symbol = stock?.getCurrencySymbol() ?: "$"
    return "%.2f%s".format(this, symbol)
}

fun Double.toPercent(): String {
    return "%.2f%%".format(this)
}

class Utils {
    companion object {
        val GREEN: Int = Color.parseColor("#58D68D")
        val RED: Int = Color.parseColor("#E74C3C")

        fun getColorForValue(value: Double): Int {
            return if (value < 0) RED else GREEN
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

        fun showToastAlert(text: String) {
            Toast.makeText(TheApplication.application.applicationContext, text, LENGTH_SHORT).show()
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
            alertDialog.setTitle("Новая версия!")
            alertDialog.setMessage("Хотите обновиться? \n$from >>> $to")
            alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "ДА!") { dialog, _ ->
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/oostap/2358/releases/latest"))
                context.startActivity(browserIntent)
                dialog.cancel() }
            alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "НЕТ.") { dialog, _ -> dialog.cancel() }
            alertDialog.show()
        }

        fun openTinkoffForTicker(context: Context, ticker: String) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tinkoff.ru/invest/stocks/$ticker/"))
            context.startActivity(browserIntent)
        }

        fun getTimeMSK(): Calendar {
            return Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow"))
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

            if (hour < 7 || hour > 2) {
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

        fun makeNicePrice(price: Double): Double {
            return (price * 100.0).roundToInt() / 100.0
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
                Interval.FOUR_HOURS -> "5hours"
                Interval.MONTH -> "month"
            }
        }

        fun convertStringToInterval(interval: String): Interval? {
            when (interval) {
                "1min" -> {
                    return Interval.MINUTE
                }
                "hour" -> {
                    return Interval.HOUR
                }
                "2hour" -> {
                    return Interval.TWO_HOURS
                }
                "day" -> {
                    return Interval.DAY
                }
                "week" -> {
                    return Interval.WEEK
                }
            }

            return null
        }

        fun search(stocks: List<Stock>, text: String): MutableList<Stock> {
            if (text.isNotEmpty()) {
                val list = stocks.filter {
                            it.instrument.ticker.contains(text, ignoreCase = true) ||
                            it.instrument.name.contains(text, ignoreCase = true) ||
                            it.alterName.contains(text, ignoreCase = true)
                }.toMutableList()
                list.sortBy { it.instrument.ticker.length }
                return list
            }
            return stocks.toMutableList()
        }
    }
}