package com.project.ti2358.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.PowerManager
import android.widget.Toast
import com.project.ti2358.data.manager.StrategyTazik
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.internal.notify
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class StrategyTazikService : Service() {

    private val NOTIFICATION_CANCEL_ACTION = "event.tazik"
    private val NOTIFICATION_CHANNEL_ID = "TAZIK CHANNEL NOTIFICATION"
    private val NOTIFICATION_ID = 100011

    private val strategyTazik: StrategyTazik by inject()

    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceRunning = false
    private var notificationButtonReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val intentFilter = IntentFilter(NOTIFICATION_CANCEL_ACTION)
        notificationButtonReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val type = intent.getStringExtra("type")
                if (type == "cancel") {
                    if (notificationButtonReceiver != null) unregisterReceiver(notificationButtonReceiver)
                    notificationButtonReceiver = null
                    context.stopService(Intent(context, StrategyTazikService::class.java))
                    strategyTazik.stopStrategy()
                }
            }
        }
        registerReceiver(notificationButtonReceiver, intentFilter)

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        val notification = Utils.createNotification(this, NOTIFICATION_CHANNEL_ID, NOTIFICATION_CANCEL_ACTION, "Тазик", "", "", "")
        startForeground(NOTIFICATION_ID, notification)

        scheduleUpdate()
    }

    override fun onDestroy() {
        Toast.makeText(this, "Покупка тазика отменена", Toast.LENGTH_LONG).show()
        if (notificationButtonReceiver != null) unregisterReceiver(notificationButtonReceiver)
        notificationButtonReceiver = null
        isServiceRunning = false

        super.onDestroy()
    }

    private fun scheduleUpdate() {
        Toast.makeText(this, "Запущен тазик на покупку просадок", Toast.LENGTH_LONG).show()
        isServiceRunning = true

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EndlessService::lock").apply {
                acquire(10 * 10 * 1000)
            }
        }

        GlobalScope.launch(Dispatchers.Main) {
            while (isServiceRunning) {
                val seconds = updateNotification()
                delay(1 * 1000 * seconds)
            }
        }
    }

    private fun updateNotification(): Long {
        val title = strategyTazik.getNotificationTitle()

        val longText: String = strategyTazik.getNotificationTextLong()
        val shortText: String = strategyTazik.getNotificationTextShort()
        val longTitleText: String = strategyTazik.getTotalPurchaseString()

        val notification = Utils.createNotification(
            this,
            NOTIFICATION_CHANNEL_ID, NOTIFICATION_CANCEL_ACTION,
            title, shortText, longText, longTitleText
        )

        synchronized(notification) {
            notification.notify()
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        }

        return 1
    }
}
