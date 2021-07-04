package com.project.ti2358.data.manager

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import com.project.ti2358.MainActivity
import com.project.ti2358.R
import com.project.ti2358.TheApplication
import com.project.ti2358.data.manager.*
import com.project.ti2358.service.StrategyArbitrationService
import com.project.ti2358.service.Utils
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
class StrategyArbitration : KoinComponent {
    private val stockManager: StockManager by inject()
    private val strategySpeaker: StrategySpeaker by inject()
    private val strategyTelegram: StrategyTelegram by inject()

    var stocks: MutableList<Stock> = mutableListOf()
    var stocksSelected: MutableList<Stock> = synchronizedList(mutableListOf())
    var longStocks: MutableList<StockArbitration> = synchronizedList(mutableListOf())
    var shortStocks: MutableList<StockArbitration> = synchronizedList(mutableListOf())

    var started: Boolean = false

    suspend fun process(): MutableList<Stock> = withContext(StockManager.stockContext) {
        val all = stockManager.getWhiteStocks()

        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        stocks.clear()
        stocks.addAll(all.filter { it.getPriceNow() > min && it.getPriceNow() < max })

        return@withContext stocks
    }

    suspend fun restartStrategy() = withContext(StockManager.stockContext) {
        if (started) stopStrategy()
        delay(500)
        startStrategy()
    }

    suspend fun stopStrategyCommand() = withContext(StockManager.stockContext) {
        Utils.stopService(TheApplication.application.applicationContext, StrategyArbitrationService::class.java)
    }

    suspend fun startStrategy() = withContext(StockManager.stockContext) {
        longStocks.clear()
        shortStocks.clear()

        process()

        stockManager.subscribeOrderbookRU(stockManager.stocksStream)

        started = true
        strategyTelegram.sendArbitrationStart(true)
    }

    fun stopStrategy() {
        started = false
        strategyTelegram.sendArbitrationStart(false)
        stockManager.unsubscribeOrderbookAllRU()
    }

    fun processStrategy(stock: Stock) {
        if (!started) return
        if (stocks.isEmpty()) runBlocking { process() }
        if (stock !in stocks) return

        if (stock.orderbookStream == null) return

        if (stock.orderbookStream == null || stock.orderbookStream?.asks?.isEmpty() == true || stock.orderbookStream?.bids?.isEmpty() == true) return

        val minPercent = SettingsManager.getArbitrationMinPercent()
        val repeatInterval = SettingsManager.getArbitrationRepeatInterval()
        val long = SettingsManager.getArbitrationLong()
        val short = SettingsManager.getArbitrationShort()
        val volumeFrom = SettingsManager.getArbitrationVolumeDayFrom()
        val volumeTo = SettingsManager.getArbitrationVolumeDayTo()

        if (stock.getTodayVolume() < volumeFrom || stock.getTodayVolume() > volumeTo) return

        val askRU = stock.orderbookStream?.asks?.first()?.get(0) ?: 0.0
        if (askRU == 0.0) return

        val bidRU = stock.orderbookStream?.bids?.first()?.get(0) ?: 0.0
        if (bidRU == 0.0) return

        val priceUS = stock.closePrices?.yahoo ?: 0.0
        if (priceUS == 0.0) return

        val time = Calendar.getInstance().time

        var arbStock: StockArbitration? = null

        if (askRU < priceUS && long) { // long
            val changePercent = priceUS / askRU * 100.0 - 100.0
            if (abs(changePercent) < abs(minPercent)) return

            arbStock = StockArbitration(stock, askRU, bidRU, priceUS, true, time.time)
            arbStock.changePricePercent = changePercent
            arbStock.changePriceAbsolute = priceUS - askRU

            val last = longStocks.firstOrNull { it.stock.ticker == stock.ticker }
            if (last != null) {
                val deltaTime = ((time.time - last.fireTime) / 60.0 / 1000.0).toInt()
                if (deltaTime < repeatInterval) return
            }

            longStocks.add(0, arbStock)
        } else if (bidRU > priceUS && stock.short != null && short) { // short
            val changePercent = priceUS / bidRU * 100.0 - 100.0
            if (abs(changePercent) < abs(minPercent)) return

            arbStock = StockArbitration(stock, askRU, bidRU, priceUS, false, time.time)
            arbStock.changePricePercent = changePercent
            arbStock.changePriceAbsolute = bidRU - priceUS

            val last = shortStocks.firstOrNull { it.stock.ticker == stock.ticker }
            if (last != null) {
                val deltaTime = ((time.time - last.fireTime) / 60.0 / 1000.0).toInt()
                if (deltaTime < repeatInterval) return
            }

            shortStocks.add(0, arbStock)
        }

        if (arbStock != null) {
            GlobalScope.launch(Dispatchers.Main) {
//            strategySpeaker.speakRocket(arbStock)
                strategyTelegram.sendArbitration(arbStock)
                createArbitration(arbStock)
            }
        }
    }

    private fun createArbitration(stockArbitration: StockArbitration) {
        val context: Context = TheApplication.application.applicationContext

        val ticker = stockArbitration.ticker
        val notificationChannelId = ticker

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                notificationChannelId,
                "Arbitration notifications channel $ticker",
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

        val changePercent = if (stockArbitration.changePricePercent > 0) {
            "+%.2f%%".format(locale = Locale.US, stockArbitration.changePricePercent)
        } else {
            "%.2f%%".format(locale = Locale.US, stockArbitration.changePricePercent)
        }

        val title = "$ticker: ${stockArbitration.askRU.toMoney(stockArbitration.stock)} -> ${stockArbitration.priceUS.toMoney(stockArbitration.stock)} = $changePercent"

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