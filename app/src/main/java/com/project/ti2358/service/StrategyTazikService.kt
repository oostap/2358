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
import kotlinx.coroutines.*
import okhttp3.internal.notify
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class StrategyTazikService : Service() {
    companion object {
        private const val NOTIFICATION_ACTION_FILTER = "event.tazik"
        private const val NOTIFICATION_ACTION_PLUS = "event.tazik.plus"
        private const val NOTIFICATION_ACTION_MINUS = "event.tazik.minus"
        private const val NOTIFICATION_ACTION_CANCEL = "event.tazik.cancel"
        private const val NOTIFICATION_ACTION_BUY = "event.tazik.buy"

        private const val NOTIFICATION_CHANNEL_ID = "TAZIK CHANNEL NOTIFICATION"
        private const val NOTIFICATION_ID = 100011
    }

    private val strategyTazik: StrategyTazik by inject()

    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceRunning = false
    private var notificationButtonReceiver: BroadcastReceiver? = null

    var job: Job? = null
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val intentFilter = IntentFilter(NOTIFICATION_ACTION_FILTER)
        notificationButtonReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val type = intent.getStringExtra("type")
                if (type == NOTIFICATION_ACTION_CANCEL) {
                    if (notificationButtonReceiver != null) unregisterReceiver(notificationButtonReceiver)
                    notificationButtonReceiver = null
                    context.stopService(Intent(context, StrategyTazikService::class.java))
                }

                if (type == NOTIFICATION_ACTION_PLUS) {
                    strategyTazik.addBasicPercentLimitPriceChange(1)
                    updateNotification()
                }

                if (type == NOTIFICATION_ACTION_MINUS) {
                    strategyTazik.addBasicPercentLimitPriceChange(-1)
                    updateNotification()
                }

                if (type == NOTIFICATION_ACTION_BUY) {
                    strategyTazik.buyFirstOne()
                    updateNotification()
                }
            }
        }
        registerReceiver(notificationButtonReceiver, intentFilter)

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        val notification = Utils.createNotification(this, NOTIFICATION_CHANNEL_ID, "Тазик", "", "", "")
        startForeground(NOTIFICATION_ID, notification)

        scheduleUpdate()
    }

    override fun onDestroy() {
        Toast.makeText(this, "Тазики убраны", Toast.LENGTH_SHORT).show()
        if (notificationButtonReceiver != null) unregisterReceiver(notificationButtonReceiver)
        notificationButtonReceiver = null
        isServiceRunning = false
        job?.cancel()
        GlobalScope.launch {
            strategyTazik.stopStrategy()
        }
        super.onDestroy()
    }

    private fun scheduleUpdate() {
        Toast.makeText(this, "Запущены тазики на покупку просадок", Toast.LENGTH_SHORT).show()
        isServiceRunning = true

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EndlessService::lock").apply {
                acquire(10 * 10 * 1000)
            }
        }

        job?.cancel()
        job = GlobalScope.launch(Dispatchers.Main) {
            while (isServiceRunning) {
                val seconds = updateNotification()
                delay(1 * 1000 * seconds)
            }
        }
    }

    private fun updateNotification(): Long {
        strategyTazik.processUpdate()

        val title = strategyTazik.getNotificationTitle()
        val longText: String = strategyTazik.getNotificationTextLong()
        val shortText: String = strategyTazik.getNotificationTextShort()
        val longTitleText: String = strategyTazik.getTotalPurchaseString()

        val cancelIntent = Intent(NOTIFICATION_ACTION_FILTER).apply { putExtra("type", NOTIFICATION_ACTION_CANCEL) }
        val pendingCancelIntent = PendingIntent.getBroadcast(this, 1, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val actionCancel: Notification.Action = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Notification.Action.Builder(null, "СТОП", pendingCancelIntent).build()
        } else {
            Notification.Action.Builder(0, "СТОП", pendingCancelIntent).build()
        }

        val plusIntent = Intent(NOTIFICATION_ACTION_FILTER).apply { putExtra("type", NOTIFICATION_ACTION_PLUS) }
        val pendingPlusIntent = PendingIntent.getBroadcast(this, 2, plusIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val actionPlus: Notification.Action = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Notification.Action.Builder(null, "  +${StrategyTazik.PercentLimitChangeDelta}  ", pendingPlusIntent).build()
        } else {
            Notification.Action.Builder(0, "  +${StrategyTazik.PercentLimitChangeDelta}  ", pendingPlusIntent).build()
        }

        val buyIntent = Intent(NOTIFICATION_ACTION_FILTER).apply { putExtra("type", NOTIFICATION_ACTION_BUY) }
        val pendingBuyIntent = PendingIntent.getBroadcast(this, 4, buyIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        var actionBuy: Notification.Action? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Notification.Action.Builder(null, "ТАРИМ ПЕРВУЮ", pendingBuyIntent).build()
        } else {
            Notification.Action.Builder(0, "ТАРИМ ПЕРВУЮ", pendingBuyIntent).build()
        }

        if (!strategyTazik.started) {
            actionBuy = null
        }

        val notification = Utils.createNotification(this, NOTIFICATION_CHANNEL_ID, title, shortText, longText, longTitleText, actionCancel, actionPlus, /*actionMinus,*/ actionBuy) // больше трёх кнопок нельзя
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)

        return 1
    }
}
