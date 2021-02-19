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
import androidx.preference.PreferenceManager
import com.project.ti2358.BuildConfig
import com.project.ti2358.MainActivity
import com.project.ti2358.R
import com.project.ti2358.data.service.SettingsManager
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.component.KoinApiExtension
import java.text.SimpleDateFormat
import java.util.*
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
    companion object {
        val GREEN: Int = Color.parseColor("#58D68D")
        val RED: Int = Color.parseColor("#E74C3C")

        lateinit var context: Context
        fun setApplicationContext(applicationContext : Context) {
            context = applicationContext
        }

        fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
            for (service in manager!!.getRunningServices(Int.MAX_VALUE)) {
                if (serviceClass.name == service.service.className) {
                    return true
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
            Toast.makeText(context, text, LENGTH_SHORT).show()
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

            if (hour == 9 && minute > 55) {
                return false
            }

            if (hour < 10 || hour > 2) {
                return true
            }

            log("MSK: $hour:$minute")
            return false
        }

        fun isHighSpeedSession(): Boolean {
            val msk = getTimeMSK()
            val hour = msk.get(Calendar.HOUR_OF_DAY)
            val minute = msk.get(Calendar.MINUTE)

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

            if (hour >= 10 || hour <= 2) {
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
    }
}