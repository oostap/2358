package com.project.ti2358.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.widget.Toast
import com.project.ti2358.MainActivity
import com.project.ti2358.R
import com.project.ti2358.data.manager.StrategyTelegramCommands
import com.project.ti2358.data.manager.StrategyTelegram
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class StrategyTelegramService : Service() {

    private val NOTIFICATION_ACTION = "event.telegram"
    private val NOTIFICATION_CANCEL_ACTION = "event.telegram.cancel"
    private val NOTIFICATION_CHANNEL_ID = "TELEGRAM CHANNEL NOTIFICATION"
    private val NOTIFICATION_ID = 17001131

    private val strategyTelegram: StrategyTelegram by inject()
    private val strategyTelegramCommands: StrategyTelegramCommands by inject()

    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceRunning = false
    private var notificationButtonReceiver: BroadcastReceiver? = null

    var job: Job? = null

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val intentFilter = IntentFilter(NOTIFICATION_ACTION)
        notificationButtonReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val type = intent.getStringExtra("type")
                if (type == NOTIFICATION_CANCEL_ACTION) {
                    if (notificationButtonReceiver != null) unregisterReceiver(
                        notificationButtonReceiver
                    )
                    notificationButtonReceiver = null
                    context.stopService(Intent(context, StrategyTelegramService::class.java))
                }
            }
        }
        registerReceiver(notificationButtonReceiver, intentFilter)

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        val notification = Utils.createNotification(this, NOTIFICATION_CHANNEL_ID, "TELEGRAM","",  "", "")
        startForeground(NOTIFICATION_ID, notification)
        strategyTelegram.startStrategy()
        strategyTelegramCommands.startStrategy()
        scheduleUpdate()
    }

    override fun onDestroy() {
        Toast.makeText(this, "Телеграм бот отключен", Toast.LENGTH_LONG).show()
        if (notificationButtonReceiver != null) unregisterReceiver(notificationButtonReceiver)
        notificationButtonReceiver = null
        isServiceRunning = false
        job?.cancel()
        strategyTelegram.stopStrategy()
        strategyTelegramCommands.stopStrategy()
        super.onDestroy()
    }

    private fun scheduleUpdate() {
        Toast.makeText(this, "Телеграм бот запущен!", Toast.LENGTH_LONG).show()
        isServiceRunning = true

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EndlessService::lock").apply {
                acquire(10*10*1000L /*10 minutes*/)
            }
        }

        job?.cancel()
        job = GlobalScope.launch(Dispatchers.Main) {
            var count = 10
            while (count > 0) {
                delay(1000)
                updateNotification()
                count--
            }
        }
    }

    private fun stopService() {
        Toast.makeText(this, "Телеграм бот остановлен!", Toast.LENGTH_SHORT).show()
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

    private fun updateNotification() {
        val title = "Работает телеграм бот!"

        val cancelIntent = Intent(NOTIFICATION_ACTION).apply { putExtra("type", NOTIFICATION_CANCEL_ACTION) }
        val pendingCancelIntent = PendingIntent.getBroadcast(this, 1, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val actionCancel: Notification.Action = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Notification.Action.Builder(null, "СТОП", pendingCancelIntent).build()
        } else {
            Notification.Action.Builder(0, "СТОП", pendingCancelIntent).build()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "2358 notifications channel",
                NotificationManager.IMPORTANCE_HIGH
            ).let {
                it.description = NOTIFICATION_CHANNEL_ID
                it.lightColor = Color.RED
                it.enableVibration(false)
                it
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, 0)
        }

        val builder: Notification.Builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, NOTIFICATION_CHANNEL_ID) else Notification.Builder(this)

        builder
            .setSubText(title)
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOnlyAlertOnce(true)
            .setOngoing(false)
            .addAction(actionCancel)

        val notification = builder.build()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }
}
