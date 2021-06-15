package com.project.ti2358.data.manager

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import com.project.ti2358.MainActivity
import com.project.ti2358.R
import com.project.ti2358.TheApplication
import com.project.ti2358.service.ScreenerType
import com.project.ti2358.service.Sorting
import com.project.ti2358.service.Utils
import com.project.ti2358.service.toMoney
import kotlinx.coroutines.*
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*
import kotlin.math.abs
import kotlin.random.Random

@KoinApiExtension
class StrategyLimits : KoinComponent {
    private val stockManager: StockManager by inject()
    private val strategySpeaker: StrategySpeaker by inject()
    private val strategyTelegram: StrategyTelegram by inject()

    var stocks: MutableList<Stock> = mutableListOf()
    var upLimitStocks: MutableList<LimitStock> = Collections.synchronizedList(mutableListOf())
    var downLimitStocks: MutableList<LimitStock> = Collections.synchronizedList(mutableListOf())
    private var currentSort: Sorting = Sorting.DESCENDING

    private var started: Boolean = false

    suspend fun process(): MutableList<Stock> = withContext(StockManager.limitsContext) {
        val all = stockManager.getWhiteStocks()

        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        stocks.clear()
        stocks.addAll(all.filter { it.getPriceNow() > min && it.getPriceNow() < max })

        return@withContext stocks
    }

    fun resort(): MutableList<Stock> {
        currentSort = if (currentSort == Sorting.DESCENDING) Sorting.ASCENDING else Sorting.DESCENDING
        val temp = stocks

        // удалить все бумаги, где лимиты неизвестны
        temp.removeAll { it.stockInfo?.limit_up == 0.0 && it.stockInfo?.limit_down == 0.0 }

        temp.sortBy {
            val sign = if (currentSort == Sorting.ASCENDING) 1 else -1
            Utils.getPercentFromTo(it.stockInfo?.limit_up ?: 0.0, it.getPriceRaw()) * sign
//            it.changePriceScreenerPercent * sign
        }

        return stocks
    }

    suspend fun startStrategy() = withContext(StockManager.limitsContext) {
        upLimitStocks.clear()
        downLimitStocks.clear()

        process()

        started = true
        strategyTelegram.sendLimitsStart(true)
    }

    fun stopStrategy() {
        started = false
        strategyTelegram.sendLimitsStart(false)
    }

    fun processStrategy(stock: Stock) {
        if (!started) return
        if (stocks.isEmpty()) runBlocking { process() }
        if (stock !in stocks) return

        if (SettingsManager.getRocketOnlyLove()) {
            if (StrategyLove.stocksSelected.find { it.ticker == stock.ticker } == null) return
        }

        val percentRocket = SettingsManager.getRocketChangePercent()
        var minutesRocket = SettingsManager.getRocketChangeMinutes()
        val volumeRocket = SettingsManager.getRocketChangeVolume()

        if (stock.minuteCandles.isEmpty()) return



        GlobalScope.launch(Dispatchers.Main) {
//            strategySpeaker.speakRocket(rocketStock)
//            strategyTelegram.sendRocket(rocketStock)
//            createRocket(rocketStock)
        }
    }

    private fun createRocket(rocketStock: RocketStock) {
        val context: Context = TheApplication.application.applicationContext

        val ticker = rocketStock.ticker
        val notificationChannelId = ticker

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                notificationChannelId,
                "Rocket notifications channel $ticker",
                NotificationManager.IMPORTANCE_HIGH
            ).let {
                it.description = notificationChannelId
                it.lightColor = Color.RED
                it.enableVibration(true)
                it.enableLights(true)
                it
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent: PendingIntent = Intent(context, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(context, 0, notificationIntent, 0)
        }

        val builder: Notification.Builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, notificationChannelId) else Notification.Builder(context)

        val changePercent = if (rocketStock.changePercent > 0) {
            "+%.2f%%".format(locale = Locale.US, rocketStock.changePercent)
        } else {
            "%.2f%%".format(locale = Locale.US, rocketStock.changePercent)
        }

        val title = "$ticker: ${rocketStock.priceFrom.toMoney(rocketStock.stock)} -> ${rocketStock.priceTo.toMoney(rocketStock.stock)} = $changePercent за ${rocketStock.time} мин"

        val notification = builder
            .setSubText("$$ticker $changePercent")
            .setContentTitle(title)
            .setShowWhen(true)
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOnlyAlertOnce(true)
            .setOngoing(false)
            .build()

        val manager = context.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
        val uid = Random.nextInt(0, 100000)
        manager.notify(ticker, uid, notification)

        val alive: Long = SettingsManager.getRocketNotifyAlive().toLong()
        GlobalScope.launch(Dispatchers.Main) {
            delay(1000 * alive)
            manager.cancel(ticker, uid)
        }
    }
}