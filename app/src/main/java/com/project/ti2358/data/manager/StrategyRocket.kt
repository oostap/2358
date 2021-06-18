package com.project.ti2358.data.manager

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import com.project.ti2358.MainActivity
import com.project.ti2358.R
import com.project.ti2358.TheApplication
import com.project.ti2358.service.toMoney
import kotlinx.coroutines.*
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*
import java.util.Collections.synchronizedList
import kotlin.math.abs
import kotlin.random.Random

@KoinApiExtension
class StrategyRocket : KoinComponent {
    private val stockManager: StockManager by inject()
    private val strategySpeaker: StrategySpeaker by inject()
    private val strategyTelegram: StrategyTelegram by inject()

    var stocks: MutableList<Stock> = mutableListOf()
    var stocksSelected: MutableList<Stock> = synchronizedList(mutableListOf())
    var rocketStocks: MutableList<RocketStock> = synchronizedList(mutableListOf())
    var cometStocks: MutableList<RocketStock> = synchronizedList(mutableListOf())

    private var started: Boolean = false

    suspend fun process(): MutableList<Stock> = withContext(StockManager.rocketContext) {
        val all = stockManager.getWhiteStocks()

        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        stocks.clear()
        stocks.addAll(all.filter { it.getPriceNow() > min && it.getPriceNow() < max })

        return@withContext stocks
    }

    suspend fun restartStrategy() = withContext(StockManager.rocketContext) {
        stopStrategy()
        delay(500)
        startStrategy()
    }

    suspend fun startStrategy() = withContext(StockManager.rocketContext) {
        rocketStocks.clear()
        cometStocks.clear()

        process()

        started = true
        strategyTelegram.sendRocketStart(true)
    }

    fun stopStrategy() {
        started = false
        strategyTelegram.sendRocketStart(false)
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

        var fromCandle = stock.minuteCandles.last()
        val toCandle = stock.minuteCandles.last()

        var fromIndex = 0
        for (i in stock.minuteCandles.indices.reversed()) {
            if (stock.minuteCandles[i].lowestPrice < fromCandle.lowestPrice) {
                fromCandle = stock.minuteCandles[i]
                fromIndex = i
            }

            minutesRocket--
            if (minutesRocket == 0) break
        }

        val deltaMinutes = ((toCandle.time.time - fromCandle.time.time) / 60.0 / 1000.0).toInt()

        var volume = 0
        for (i in fromIndex until stock.minuteCandles.size) {
            volume += stock.minuteCandles[i].volume
        }

        val changePercent = toCandle.closingPrice / fromCandle.openingPrice * 100.0 - 100.0
        if (volume < volumeRocket || abs(changePercent) < abs(percentRocket)) return

        val rocketStock = RocketStock(stock, fromCandle.openingPrice, toCandle.closingPrice, deltaMinutes, volume, changePercent, toCandle.time.time)
        rocketStock.process()

        if (changePercent > 0) {
            val last = rocketStocks.firstOrNull { it.stock.ticker == stock.ticker }
            if (last != null) {
                val deltaTime = ((toCandle.time.time - last.fireTime) / 60.0 / 1000.0).toInt()
                if (deltaTime < 5) return
            }

            rocketStocks.add(0, rocketStock)
        } else {
            val last = cometStocks.firstOrNull { it.stock.ticker == stock.ticker }
            if (last != null) {
                val deltaTime = ((toCandle.time.time - last.fireTime) / 60.0 / 1000.0).toInt()
                if (deltaTime < 5) return
            }

            cometStocks.add(0, rocketStock)
        }

        GlobalScope.launch(Dispatchers.Main) {
            strategySpeaker.speakRocket(rocketStock)
            strategyTelegram.sendRocket(rocketStock)
            createRocket(rocketStock)
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