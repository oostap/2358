package com.project.ti2358.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.PowerManager
import android.widget.Toast
import com.project.ti2358.data.manager.Strategy2358
import com.project.ti2358.data.manager.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.internal.notify
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.lang.Integer.parseInt
import java.util.*

@KoinApiExtension
class Strategy2358Service : Service() {

    private val NOTIFICATION_CANCEL_ACTION = "event.2358"
    private val NOTIFICATION_CHANNEL_ID = "2358 CHANNEL NOTIFICATION"
    private val NOTIFICATION_ID = 2358

    private val strategy2358: Strategy2358 by inject()

    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceRunning = false
    private lateinit var schedulePurchaseTime: Calendar
    private var notificationButtonReceiver: BroadcastReceiver? = null
    private var timerBuy: Timer? = null

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
                    context.stopService(Intent(context, Strategy2358Service::class.java))
                }
            }
        }
        registerReceiver(notificationButtonReceiver, intentFilter)

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        val notification = Utils.createNotification(this, NOTIFICATION_CHANNEL_ID, NOTIFICATION_CANCEL_ACTION, "2358","",  "", "")
        startForeground(NOTIFICATION_ID, notification)

        schedulePurchase()
    }

    override fun onDestroy() {
        Toast.makeText(this, "Покупка 2358 отменена", Toast.LENGTH_LONG).show()
        if (notificationButtonReceiver != null) unregisterReceiver(notificationButtonReceiver)
        notificationButtonReceiver = null
        isServiceRunning = false

        timerBuy?.let {
            it.cancel()
            it.purge()
        }

        super.onDestroy()
    }

    private fun schedulePurchase() {
        Toast.makeText(this, "Запущен таймер на покупку 2358", Toast.LENGTH_LONG).show()
        isServiceRunning = true

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EndlessService::lock").apply {
                acquire(10 * 10 * 1000)
            }
        }

        val differenceHours: Int = Utils.getTimeDiffBetweenMSK()

        val time = SettingsManager.get2358PurchaseTime()
        val dayTime = time.split(":").toTypedArray()
        if (dayTime.size < 3) {
            stopService()
            return
        }

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

        timerBuy = Timer()
        timerBuy?.schedule(object : TimerTask() {
            override fun run() {
                val localPurchases = strategy2358.getPurchaseStock(false)
                for (purchase in localPurchases) {
                    purchase.buyLimit2358()
                }
            }
        }, scheduleDelay)

        GlobalScope.launch(Dispatchers.IO) {
            while (isServiceRunning) {
                val delaySeconds = updateNotification()
                delay(1 * 1000 * delaySeconds)
            }
        }
    }

    private fun stopService() {
        Toast.makeText(this, "2358 остановлена", Toast.LENGTH_SHORT).show()
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

        val title = if (scheduleDelay > 0) {
            "Покупка через %02d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "Покупка!"
        }

        val shortText: String = strategy2358.getNotificationTextShort()
        val longText: String = strategy2358.getNotificationTextLong()
        val longTitleText: String = "~" + strategy2358.getTotalPurchaseString() + " ="

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
