package com.project.ti2358.data.manager

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import com.project.ti2358.MainActivity
import com.project.ti2358.R
import com.project.ti2358.TheApplication
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@KoinApiExtension
class StrategyRocket() : KoinComponent {
    private val stockManager: StockManager by inject()
    var stocks: MutableList<Stock> = mutableListOf()
    var stocksSelected: MutableList<Stock> = mutableListOf()
    private var started: Boolean = false

    fun process(): MutableList<Stock> {
        val all = stockManager.getWhiteStocks()

        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        stocks.clear()
        stocks.addAll(all.filter { it.getPriceDouble() > min && it.getPriceDouble() < max })

        return stocks
    }


    fun startStrategy() {
        started = true

    }

    fun stopStrategy() {
        started = false
    }

    var counter: Int = 5
    fun processStrategy(stock: Stock) {
        if (!started) return

        var percent = SettingsManager.getRocketChangePercent()
        var minutes = SettingsManager.getRocketChangeMinutes()
        var alive = SettingsManager.getRocketNotifyAlive()

        if (counter > 0) {
            updateNotification(stock.instrument.ticker)
            counter--
        }
    }

    private fun updateNotification(title: String) {
        val notification = createNotification(TheApplication.application.applicationContext, title)
//        notification.notify()
        val manager = TheApplication.application.applicationContext.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify("text", kotlin.random.Random.Default.nextInt(0, 6000), notification)
    }

    private fun createNotification(context: Context, title: String): Notification {
        val notificationChannelId = title

        val alarmSound: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
//        val audioAttributes = AudioAttributes.Builder()
//            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
//            .setUsage(AudioAttributes.USAGE_ALARM)
//            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = TheApplication.application.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                notificationChannelId,
                "Rocket notifications channel $title",
                NotificationManager.IMPORTANCE_HIGH
            ).let {
                it.description = notificationChannelId
                it.lightColor = Color.RED
                it.enableVibration(true)
                it.enableLights(true)
//                it.setSound(alarmSound, audioAttributes)
                it
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent: PendingIntent = Intent(context, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(context, 0, notificationIntent, 0)
        }

        val builder: Notification.Builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(
            context,
            notificationChannelId
        ) else Notification.Builder(context)

        val cancelIntent = Intent("event.rocket")
        cancelIntent.putExtra("type", "cancel")
        val pendingCancelIntent = PendingIntent.getBroadcast(
            context,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        return builder
            .setShowWhen(true)
            .setTicker(title + "1")
            .setContentTitle(title + "2")
            .setContentText(title + title + "3")
            .setStyle(Notification.BigTextStyle().setSummaryText(title + "4"))
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOnlyAlertOnce(true)
            .setOngoing(false)
            .addAction(R.mipmap.ic_launcher, "СТОП", pendingCancelIntent)
            .build()
    }
}