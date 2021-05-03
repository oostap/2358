package com.project.ti2358.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.PowerManager
import android.widget.Toast
import com.project.ti2358.data.manager.StrategyTazikEndless
import kotlinx.coroutines.*
import okhttp3.internal.notify
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class StrategyTazikEndlessService : Service() {
    companion object {
        private const val NOTIFICATION_ACTION_FILTER = "event.tazikendless"
        private const val NOTIFICATION_ACTION_PLUS = "event.tazikendless.plus"
        private const val NOTIFICATION_ACTION_MINUS = "event.tazikendless.minus"
        private const val NOTIFICATION_ACTION_CANCEL = "event.tazikendless.cancel"
        private const val NOTIFICATION_ACTION_BUY = "event.tazikendless.buy"

        private const val NOTIFICATION_CHANNEL_ID = "TAZIK ENDLESS CHANNEL NOTIFICATION"
        private const val NOTIFICATION_ID = 1000111
    }

    private val strategyTazikEndless: StrategyTazikEndless by inject()

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
                    context.stopService(Intent(context, StrategyTazikEndlessService::class.java))
                }

                if (type == NOTIFICATION_ACTION_PLUS) {
                    strategyTazikEndless.addBasicPercentLimitPriceChange(1)
                    updateNotification()
                }

                if (type == NOTIFICATION_ACTION_MINUS) {
                    strategyTazikEndless.addBasicPercentLimitPriceChange(-1)
                    updateNotification()
                }

//                if (type == NOTIFICATION_ACTION_BUY) {
//                    strategyTazikEndless.buyFirstOne()
//                    updateNotification()
//                }
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
        Toast.makeText(this, "Тазики убраны", Toast.LENGTH_LONG).show()
        if (notificationButtonReceiver != null) unregisterReceiver(notificationButtonReceiver)
        notificationButtonReceiver = null
        isServiceRunning = false
        job?.cancel()
        GlobalScope.launch {
            strategyTazikEndless.stopStrategy()
        }
        super.onDestroy()
    }

    private fun scheduleUpdate() {
        Toast.makeText(this, "Запущены тазики на покупку просадок", Toast.LENGTH_LONG).show()
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
                delay(1 * 250 * seconds)
            }
        }
    }

    private fun updateNotification(): Long {
        strategyTazikEndless.processUpdate()

        val title = strategyTazikEndless.getNotificationTitle()
        val longText: String = strategyTazikEndless.getNotificationTextLong()
        val shortText: String = strategyTazikEndless.getNotificationTextShort()
        val longTitleText: String = strategyTazikEndless.getTotalPurchaseString()

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
            Notification.Action.Builder(null, "  +${StrategyTazikEndless.PercentLimitChangeDelta}  ", pendingPlusIntent).build()
        } else {
            Notification.Action.Builder(0, "  +${StrategyTazikEndless.PercentLimitChangeDelta}  ", pendingPlusIntent).build()
        }

        val minusIntent = Intent(NOTIFICATION_ACTION_FILTER).apply { putExtra("type", NOTIFICATION_ACTION_MINUS) }
        val pendingMinusIntent = PendingIntent.getBroadcast(this, 3, minusIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val actionMinus: Notification.Action = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Notification.Action.Builder(null, "  -${StrategyTazikEndless.PercentLimitChangeDelta}  ", pendingMinusIntent).build()
        } else {
            Notification.Action.Builder(0, "  -${StrategyTazikEndless.PercentLimitChangeDelta}  ", pendingMinusIntent).build()
        }

        val notification = Utils.createNotification(this, NOTIFICATION_CHANNEL_ID, title, shortText, longText, longTitleText, actionCancel, actionPlus, actionMinus)

        synchronized(notification) {
            notification.notify()
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        }

        return 1
    }
}
