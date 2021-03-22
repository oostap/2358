package com.project.ti2358.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.PowerManager
import android.widget.Toast
import com.project.ti2358.data.manager.StrategyTrailingStop
import kotlinx.coroutines.*
import okhttp3.internal.notify
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.util.*

@KoinApiExtension
class StrategyTrailingStopService : Service() {

    private val NOTIFICATION_ACTION = "event.ts"
    private val NOTIFICATION_CANCEL_ACTION = "event.ts.cancel"
    private val NOTIFICATION_CHANNEL_ID = "TS CHANNEL NOTIFICATION"
    private val NOTIFICATION_ID = 170011

    private val strategyTrailingStop: StrategyTrailingStop by inject()

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
                    context.stopService(Intent(context, StrategyTrailingStopService::class.java))
                }
            }
        }
        registerReceiver(notificationButtonReceiver, intentFilter)

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        val notification = Utils.createNotification(this, NOTIFICATION_CHANNEL_ID, "TS","",  "", "")
        startForeground(NOTIFICATION_ID, notification)
        scheduleUpdate()
    }

    override fun onDestroy() {
        job?.cancel()
        strategyTrailingStop.stopStrategy()
        Toast.makeText(this, "Трейлинг стоп отменён", Toast.LENGTH_LONG).show()
        if (notificationButtonReceiver != null) unregisterReceiver(notificationButtonReceiver)
        notificationButtonReceiver = null
        isServiceRunning = false
        super.onDestroy()
    }

    private fun scheduleUpdate() {
        Toast.makeText(this, "Трейлинг стоп активирован", Toast.LENGTH_LONG).show()
        isServiceRunning = true

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EndlessService::lock").apply {
                acquire(10*10*1000L /*10 minutes*/)
            }
        }

        job?.cancel()
        job = GlobalScope.launch(Dispatchers.Main) {
            while (isServiceRunning) {
                updateNotification()
                delay(1000)
            }
        }
    }

    private fun stopService() {
        Toast.makeText(this, "Трейлинг стоп остановлен", Toast.LENGTH_SHORT).show()
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
        val title = strategyTrailingStop.getNotificationTitleShort()
        val shortText: String = strategyTrailingStop.getNotificationTextShort()
        val longText: String = strategyTrailingStop.getNotificationTextLong()
        val longTitleText: String = strategyTrailingStop.getNotificationTitleLong()

        val cancelIntent = Intent(NOTIFICATION_ACTION).apply { putExtra("type", NOTIFICATION_CANCEL_ACTION) }
        val pendingCancelIntent = PendingIntent.getBroadcast(this, 1, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val actionCancel: Notification.Action = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Notification.Action.Builder(null, "СТОП", pendingCancelIntent).build()
        } else {
            Notification.Action.Builder(0, "СТОП", pendingCancelIntent).build()
        }

        val notification = Utils.createNotification(this, NOTIFICATION_CHANNEL_ID, title, shortText, longText, longTitleText, actionCancel)

        synchronized(notification) {
            notification.notify()
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        }
    }
}
