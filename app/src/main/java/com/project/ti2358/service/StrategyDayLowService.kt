package com.project.ti2358.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.PowerManager
import android.widget.Toast
import com.project.ti2358.data.manager.SettingsManager
import com.project.ti2358.data.manager.StrategyDayLow
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.lang.Integer.parseInt
import java.util.*

@KoinApiExtension
class StrategyDayLowService : Service() {
    private val NOTIFICATION_ACTION_FILTER = "event.daylow"
    private val NOTIFICATION_CANCEL_ACTION = "event.daylow.cancel"
    private val NOTIFICATION_ACTION_START = "event.daylow.start_now"
    private val NOTIFICATION_CHANNEL_ID = "DAYLOW CHANNEL NOTIFICATION"
    private val NOTIFICATION_ID = 23582

    private val strategyDayLow: StrategyDayLow by inject()

    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceRunning = false
    private lateinit var schedulePurchaseTime: Calendar
    private var notificationButtonReceiver: BroadcastReceiver? = null

    var title: String = ""
    var updateTitle: Boolean = true
    var job: Job? = null

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val intentFilter = IntentFilter(NOTIFICATION_ACTION_FILTER)
        notificationButtonReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val type = intent.getStringExtra("type")
                if (type == NOTIFICATION_CANCEL_ACTION) {
                    if (notificationButtonReceiver != null) unregisterReceiver(notificationButtonReceiver)
                    notificationButtonReceiver = null
                    context.stopService(Intent(context, StrategyDayLowService::class.java))
                }

                if (type == NOTIFICATION_ACTION_START) {
                    strategyDayLow.startStrategy()
                    updateTitle = false
                    updateNotification()
                }
            }
        }
        registerReceiver(notificationButtonReceiver, intentFilter)

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        val notification = Utils.createNotification(this, NOTIFICATION_CHANNEL_ID, "DAY LOW", "", "", "")
        startForeground(NOTIFICATION_ID, notification)

        schedulePurchase()
    }

    override fun onDestroy() {
        Toast.makeText(this, "Покупка DAY LOW отменена", Toast.LENGTH_LONG).show()
        if (notificationButtonReceiver != null) unregisterReceiver(notificationButtonReceiver)
        notificationButtonReceiver = null
        isServiceRunning = false
        job?.cancel()
        strategyDayLow.stopStrategy()
        super.onDestroy()
    }

    private fun schedulePurchase() {
        Toast.makeText(this, "Запущен таймер на покупку DAY LOW", Toast.LENGTH_LONG).show()
        isServiceRunning = true

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EndlessService::lock").apply {
                acquire(10 * 10 * 1000)
            }
        }

        val time = SettingsManager.get2358PurchaseTime()
        val dayTime = time.split(":").toTypedArray()
        if (dayTime.size < 3) {
            Utils.showToastAlert("Неверный формат времени! пример: 22:58:00")
            stopService()
            return
        }

        val differenceHours: Int = Utils.getTimeDiffBetweenMSK()

        val hours = parseInt(dayTime[0])
        val minutes = parseInt(dayTime[1])
        val seconds = parseInt(dayTime[2])

        schedulePurchaseTime = Calendar.getInstance(TimeZone.getDefault())
        schedulePurchaseTime.add(Calendar.HOUR_OF_DAY, -differenceHours)
        schedulePurchaseTime.set(Calendar.HOUR_OF_DAY, hours)
        schedulePurchaseTime.set(Calendar.MINUTE, minutes)
        schedulePurchaseTime.set(Calendar.SECOND, seconds)
        schedulePurchaseTime.set(Calendar.MILLISECOND, 0)
        schedulePurchaseTime.add(Calendar.HOUR_OF_DAY, differenceHours)

        val now = Calendar.getInstance(TimeZone.getDefault())
        var scheduleDelay = schedulePurchaseTime.timeInMillis - now.timeInMillis
        if (scheduleDelay < 0) {
            schedulePurchaseTime.add(Calendar.DAY_OF_MONTH, 1)
            scheduleDelay = schedulePurchaseTime.timeInMillis - now.timeInMillis
        }

        if (scheduleDelay < 0) {
            stopService()
            return
        }

        updateTitle = true
        job?.cancel()
        job = GlobalScope.launch(Dispatchers.Main) {
            while (isServiceRunning) {
                val delaySeconds = updateNotification()
                delay(1 * 1000 * delaySeconds)
            }
        }
    }

    private fun stopService() {
        Toast.makeText(this, "DAY LOW остановлена", Toast.LENGTH_SHORT).show()
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
        val now = Calendar.getInstance(TimeZone.getDefault())
        val scheduleDelay = schedulePurchaseTime.timeInMillis - now.timeInMillis

        val allSeconds = scheduleDelay / 1000
        val hours = allSeconds / 3600
        val minutes = (allSeconds - hours * 3600) / 60
        val seconds = allSeconds % 60

        if (updateTitle) {
            if (hours + minutes + seconds <= 0) {
                strategyDayLow.startStrategy()
                updateTitle = false
                title = "Покупка!"
            } else {
                title = "Покупка через %02d:%02d:%02d".format(hours, minutes, seconds)
            }
        } else {
            title = "Покупка!"
        }

        val shortText: String = strategyDayLow.getNotificationTextShort()
        val longText: String = strategyDayLow.getNotificationTextLong()
        val longTitleText: String = "~" + strategyDayLow.getTotalPurchaseString() + " ="

        val cancelIntent = Intent(NOTIFICATION_ACTION_FILTER).apply { putExtra("type", NOTIFICATION_CANCEL_ACTION) }
        val pendingCancelIntent = PendingIntent.getBroadcast(this, 1, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val actionCancel: Notification.Action = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Notification.Action.Builder(null, "СТОП", pendingCancelIntent).build()
        } else {
            Notification.Action.Builder(0, "СТОП", pendingCancelIntent).build()
        }

        val startIntent = Intent(NOTIFICATION_ACTION_FILTER).apply { putExtra("type", NOTIFICATION_ACTION_START) }
        val pendingBuyIntent = PendingIntent.getBroadcast(this, 2, startIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val actionStart: Notification.Action = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Notification.Action.Builder(null, "СТАРТ СЕЙЧАС", pendingBuyIntent).build()
        } else {
            Notification.Action.Builder(0, "СТАРТ СЕЙЧАС", pendingBuyIntent).build()
        }

        val notification = Utils.createNotification(this, NOTIFICATION_CHANNEL_ID, title, shortText, longText, longTitleText, actionCancel, actionStart)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)

        return when {
            hours > 1 -> 10
            minutes > 10 -> 5
            minutes > 1 -> 2
            minutes < 1 -> 1
            else -> 5
        }
    }
}
