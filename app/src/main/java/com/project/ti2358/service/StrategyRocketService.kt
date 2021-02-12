package com.project.ti2358.service

import android.app.*
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.widget.Toast
import com.project.ti2358.MainActivity
import com.project.ti2358.R
import com.project.ti2358.data.manager.StrategyRocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.internal.notify
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension


@KoinApiExtension
class StrategyRocketService : Service() {

    private val NOTIFICATION_CHANNEL_ID = "ROCKET CHANNEL NOTIFICATION"
    private val NOTIFICATION_ID = 1000111

    private val strategyRocket: StrategyRocket by inject()

    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceRunning = false
    private var notificationButtonReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val intentFilter = IntentFilter("event.rocket")
        notificationButtonReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val type = intent.getStringExtra("type")
                if (type == "cancel") {
                    if (notificationButtonReceiver != null) unregisterReceiver(
                        notificationButtonReceiver
                    )
                    notificationButtonReceiver = null
                    context.stopService(Intent(context, StrategyTazikService::class.java))
                    strategyRocket.stopStrategy()
                }
            }
        }
        registerReceiver(notificationButtonReceiver, intentFilter)

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        val notification = createNotification("Rocket")
        startForeground(NOTIFICATION_ID, notification)

        scheduleUpdate()
    }

    override fun onDestroy() {
        Toast.makeText(this, "Ракеты выключены", Toast.LENGTH_LONG).show()
        if (notificationButtonReceiver != null) unregisterReceiver(notificationButtonReceiver)
        notificationButtonReceiver = null
        isServiceRunning = false

        super.onDestroy()
    }

    private fun scheduleUpdate() {
        Toast.makeText(this, "Ракета запущена", Toast.LENGTH_LONG).show()
        isServiceRunning = true

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EndlessService::lock").apply {
                acquire()
            }
        }

        GlobalScope.launch(Dispatchers.IO) {
            updateNotification()
        }
    }

    private fun stopService() {
        Toast.makeText(this, "Ракеты остановлены", Toast.LENGTH_SHORT).show()
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            stopForeground(true)
            stopSelf()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isServiceRunning = false
    }

    private fun updateNotification(): Long {
        val title = "Внимание! Работают ракеты!"

        val notification = createNotification(title)
        synchronized(notification) {
            notification.notify()
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        }

        return 5
    }

    private fun createNotification(title: String): Notification {
        val notificationChannelId = NOTIFICATION_CHANNEL_ID

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                notificationChannelId,
                "Rocket notifications channel",
                IMPORTANCE_DEFAULT
            ).let {
                it.description = notificationChannelId
                it.lightColor = Color.RED
                it.enableVibration(false)
                it
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, 0)
        }

        val builder: Notification.Builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(
            this,
            notificationChannelId
        ) else Notification.Builder(this)

        val cancelIntent = Intent("event.rocket")
        cancelIntent.putExtra("type", "cancel")
        val pendingCancelIntent = PendingIntent.getBroadcast(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

//        val longText: String = strategyTazik.getNotificationTextLong()
//        val shortText: String = strategyTazik.getNotificationTextShort()
//        val priceText: String = strategyTazik.getTotalPurchaseString()
//        builder.setSound(alarmSound)
//        builder.setDefaults(Notification.DEFAULT_SOUND);

        return builder
//            .setContentTitle(title)
//            .setContentText(shortText)
            .setStyle(Notification.BigTextStyle().setSummaryText(title))
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(R.mipmap.ic_launcher, "СТОП", pendingCancelIntent)
            .build()
    }
}
