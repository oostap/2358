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

fun Double.toDollar(): String {
    return "%.2f$".format(this)
}

fun Double.toPercent(): String {
    return "%.2f%%".format(this)
}

class Utils {
    @KoinApiExtension
    companion object {
        val GREEN: Int = Color.parseColor("#58D68D")
        val RED: Int = Color.parseColor("#E74C3C")

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
        fun createNotification(context: Context,
                               channelId: String,
                               cancelAction: String,
                               summaryTitle: String,
                               shortText: String,
                               longText: String,
                               longTitleText: String): Notification {
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

            val builder: Notification.Builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(
                context,
                channelId
            ) else Notification.Builder(context)

            val cancelIntent = Intent(cancelAction)
            cancelIntent.putExtra("type", "cancel")
            val pendingCancelIntent = PendingIntent.getBroadcast(
                context,
                1,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )

//            val longText: String = strategy1000Buy.getNotificationTextLong()
//            val shortText: String = strategy1000Buy.getNotificationTextShort()
//            val longTitleText: String = "~" + strategy1000Buy.getTotalPurchaseString() + " ="

            return builder
                .setContentText(shortText)
                .setStyle(Notification.BigTextStyle().setSummaryText(summaryTitle).bigText(longText).setBigContentTitle(longTitleText))
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOnlyAlertOnce(true)
                .setOngoing(false)
                .addAction(R.mipmap.ic_launcher, "СТОП", pendingCancelIntent)
                .build()
        }

        fun makeNicePrice(price: Double): Double {
            return (price * 100.0).roundToInt() / 100.0
        }


        fun getTimezoneMSK(): String {
            val tz = TimeZone.getTimeZone("Europe/Moscow")
            val cal = Calendar.getInstance(tz)
            val offsetInMillis = tz.getOffset(cal.timeInMillis)
            var offset = String.format("%02d:%02d", abs(offsetInMillis / 3600000), abs(offsetInMillis / 60000 % 60))
            offset = (if (offsetInMillis >= 0) "+" else "-") + offset
            return offset
        }

        fun getTimezoneCurrent(): String {
            val tz = TimeZone.getDefault()
            val cal = Calendar.getInstance(tz)
            val offsetInMillis = tz.getOffset(cal.timeInMillis)
            var offset = String.format("%02d:%02d", abs(offsetInMillis / 3600000), abs(offsetInMillis / 60000 % 60))
            offset = (if (offsetInMillis >= 0) "+" else "-") + offset
            return offset
        }

        fun getLastClosingPostmarketUSDate(): String {
            val differenceHours: Int = Utils.getTimeDiffBetweenMSK()

            val hours = 8
            val minutes = 0
            val seconds = 0

            val time = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow"))
            time.add(Calendar.HOUR_OF_DAY, -differenceHours)

            time.set(Calendar.HOUR_OF_DAY, hours)
            time.set(Calendar.MINUTE, minutes)
            time.set(Calendar.SECOND, seconds)
            time.set(Calendar.MILLISECOND, 0)

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

        fun getLastClosingDate(before: Boolean, delta: Int = 0): String {
            val differenceHours: Int = getTimeDiffBetweenMSK()

            var hours = 23
            var minutes = 59
            var seconds = 0

            if (!before) {
                hours = 0
                minutes = 0
                seconds = 0
            }

            val time = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow"))
            time.add(Calendar.HOUR_OF_DAY, -differenceHours)

            time.set(Calendar.HOUR_OF_DAY, hours)
            time.set(Calendar.MINUTE, minutes)
            time.set(Calendar.SECOND, seconds)
            time.set(Calendar.MILLISECOND, 0)

            if (delta != 0) {
                time.add(Calendar.DAY_OF_MONTH, delta)
            }

            // если воскресенье, то откатиться к субботе
            if (time.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                time.add(Calendar.DAY_OF_MONTH, -1)
            }

            // если понедельник, то откатиться к субботе
            if (time.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) {
                time.add(Calendar.DAY_OF_MONTH, -2)
            }

            if (before) {
                time.add(Calendar.DAY_OF_MONTH, -1)
            }

            return time.time.toString("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
        }

        fun convertDateToTinkoffDate(calendar: Calendar, zone: String): String {
            return calendar.time.toString("yyyy-MM-dd'T'HH:mm:ss.SSSSSS") + zone
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
                    return 60 * 60 * 24
                }
                Interval.WEEK -> {
                    return 60 * 60 * 24 * 7
                }
            }

            return 60
        }

        fun convertIntervalToString(interval: Interval): String {
            when (interval) {
                Interval.MINUTE -> {
                    return "1min"
                }
                Interval.HOUR -> {
                    return "hour"
                }
                Interval.TWO_HOURS -> {
                    return "2hour"
                }
                Interval.DAY -> {
                    return "day"
                }
                Interval.WEEK -> {
                    return "week"
                }
            }

            return "1min"
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
                return stocks.filter {
                    it.marketInstrument.ticker.contains(text, ignoreCase = true) || it.marketInstrument.name.contains(text, ignoreCase = true) ||
                    it.alterName.contains(text, ignoreCase = true)

                }.toMutableList()
            }
            return stocks.toMutableList()
        }
    }
}