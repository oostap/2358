package com.project.ti2358.service

import android.app.*
import android.app.NotificationManager.*
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
import com.project.ti2358.data.manager.Strategy1000Sell
import kotlinx.coroutines.*
import okhttp3.internal.notify
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.util.*

@KoinApiExtension
class Strategy1000SellService : Service() {

    private val NOTIFICATION_CANCEL_ACTION = "event.1000.sell"
    private val NOTIFICATION_CHANNEL_ID = "1000 SELL CHANNEL NOTIFICATION"
    private val NOTIFICATION_ID = 10000

    private val strategy1000Sell: Strategy1000Sell by inject()

    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceRunning = false
    private lateinit var schedulePurchaseTime: Calendar
    private var notificationButtonReceiver: BroadcastReceiver? = null
    var job: Job? = null

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val intentFilter = IntentFilter(NOTIFICATION_CANCEL_ACTION)
        notificationButtonReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val type = intent.getStringExtra("type")
                if (type == "cancel") {
                    if (notificationButtonReceiver != null) unregisterReceiver(
                        notificationButtonReceiver
                    )
                    notificationButtonReceiver = null
                    context.stopService(Intent(context, Strategy1000SellService::class.java))
                }
            }
        }
        registerReceiver(notificationButtonReceiver, intentFilter)

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        val notification = Utils.createNotification(this, NOTIFICATION_CHANNEL_ID, NOTIFICATION_CANCEL_ACTION, "1000 Sell", "", "", "")
        startForeground(NOTIFICATION_ID, notification)

        strategy1000Sell.startSell1000()
        scheduleSell()
    }

    override fun onDestroy() {
        Toast.makeText(this, "Продажа 1000 sell отменена", Toast.LENGTH_LONG).show()
        if (notificationButtonReceiver != null) unregisterReceiver(notificationButtonReceiver)
        notificationButtonReceiver = null
        isServiceRunning = false
        job?.cancel()
        super.onDestroy()
    }

    private fun scheduleSell() {
        Toast.makeText(this, "Запущен таймер на продажу 1000", Toast.LENGTH_LONG).show()
        isServiceRunning = true

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EndlessService::lock").apply {
                acquire(10 * 10 * 1000)
            }
        }

        val differenceHours: Int = Utils.getTimeDiffBetweenMSK()

        // 10:00:00.100
        val hours = 10
        val minutes = 0
        val seconds = 0
        val milliseconds = 100

        job = GlobalScope.launch(Dispatchers.Main) {
            schedulePurchaseTime = Calendar.getInstance(TimeZone.getDefault())
            schedulePurchaseTime.add(Calendar.HOUR_OF_DAY, -differenceHours)
            schedulePurchaseTime.set(Calendar.HOUR_OF_DAY, hours)
            schedulePurchaseTime.set(Calendar.MINUTE, minutes)
            schedulePurchaseTime.set(Calendar.SECOND, seconds)
            schedulePurchaseTime.set(Calendar.MILLISECOND, milliseconds)
            schedulePurchaseTime.add(Calendar.HOUR_OF_DAY, differenceHours)

            val now = Calendar.getInstance(TimeZone.getDefault())
            var scheduleDelay = schedulePurchaseTime.timeInMillis - now.timeInMillis
            if (scheduleDelay < 0) {
                schedulePurchaseTime.add(Calendar.DAY_OF_MONTH, 1)
                scheduleDelay = schedulePurchaseTime.timeInMillis - now.timeInMillis
            }

            if (scheduleDelay < 0) {
                stopService()
                return@launch
            }

            delay(scheduleDelay)
            for (position in strategy1000Sell.positionsToSell1000) {
                position.sell()
            }
        }

        GlobalScope.launch(Dispatchers.Main) {
            while (isServiceRunning) {
                val delaySeconds: Long = updateNotification()
                delay(1 * 1000 * delaySeconds)
            }
        }
    }

    private fun stopService() {
        Toast.makeText(this, "1000 Sell остановлена", Toast.LENGTH_SHORT).show()
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
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

        val title = if (scheduleDelay > 0) {
            "Продажа через %02d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "Продажа!"
        }

        val shortText: String = strategy1000Sell.getNotificationTextShort1000()
        val longText: String = strategy1000Sell.getNotificationTextLong1000()
        val longTitleText: String = "~" + strategy1000Sell.getTotalSellString1000() + " ="

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

        when {
            hours > 1 -> {
                return 10
            }
            minutes > 10 -> {
                return 5
            }
            minutes > 1 -> {
                return 2
            }
            minutes < 1 -> {
                return 1
            }
        }

        return 5
    }
}
