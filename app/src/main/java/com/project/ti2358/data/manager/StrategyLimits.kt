package com.project.ti2358.data.manager

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import com.project.ti2358.MainActivity
import com.project.ti2358.R
import com.project.ti2358.TheApplication
import com.project.ti2358.service.*
import kotlinx.coroutines.*
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*
import kotlin.random.Random

@KoinApiExtension
class StrategyLimits : KoinComponent {
    private val stockManager: StockManager by inject()
    private val strategySpeaker: StrategySpeaker by inject()
    private val strategyTelegram: StrategyTelegram by inject()

    var stocks: MutableList<Stock> = mutableListOf()
    var upStockLimits: MutableList<StockLimit> = Collections.synchronizedList(mutableListOf())
    var downStockLimits: MutableList<StockLimit> = Collections.synchronizedList(mutableListOf())
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

    suspend fun resort(): MutableList<Stock> = withContext(StockManager.limitsContext) {
        currentSort = if (currentSort == Sorting.DESCENDING) Sorting.ASCENDING else Sorting.DESCENDING
        val temp = stocks

        // удалить все бумаги, где лимиты неизвестны
        temp.removeAll { it.stockInfo == null || it.stockInfo?.limit_up == 0.0 && it.stockInfo?.limit_down == 0.0 }

        temp.sortBy {
            val sign = if (currentSort == Sorting.ASCENDING) 1 else -1
            Utils.getPercentFromTo(it.stockInfo?.limit_up ?: 0.0, it.getPriceRaw()) * sign
        }

        return@withContext stocks
    }

    suspend fun restartStrategy() = withContext(StockManager.limitsContext) {
        if (started) stopStrategy()
        delay(500)
        startStrategy()
    }

    suspend fun stopStrategyCommand() = withContext(StockManager.stockContext) {
        Utils.stopService(TheApplication.application.applicationContext, StrategyLimitsService::class.java)
    }

    suspend fun startStrategy() = withContext(StockManager.limitsContext) {
        upStockLimits.clear()
        downStockLimits.clear()

        process()

        started = true
        strategyTelegram.sendLimitsStart(true)

        stockManager.subscribeStockInfoAll()
    }

    fun stopStrategy() {
        started = false
        strategyTelegram.sendLimitsStart(false)

        stockManager.unsubscribeStockInfoAll()
    }

    fun processStrategy(stock: Stock) {
        if (!started || stock.stockInfo == null) return
        if (stocks.isEmpty()) runBlocking { process() }
        if (stock !in stocks) return

        val changeUpLimit = SettingsManager.getLimitsChangeDown()
        val changeDownLimit = SettingsManager.getLimitsChangeUp()
        val allowDown = SettingsManager.getLimitsDown()
        val allowUp = SettingsManager.getLimitsUp()

        if (stock.minuteCandles.isEmpty()) return

        val lastCandle = stock.minuteCandles.last()

        // минимальный объём
        if (lastCandle.volume < 50) return

        val fireTime = lastCandle.time.time

        stock.stockInfo?.let {
            var stockLimit: StockLimit? = null

            val allDistance = it.limit_up - it.limit_down
            if (allDistance == 0.0 || it.limit_up == 0.0 || it.limit_down == 0.0) return@let
            val center = it.limit_down + allDistance / 2.0
            val price = stock.getPriceRaw() * stock.instrument.lot

            val upLimitChange = Utils.getPercentFromTo(it.limit_up, price)
            val downLimitChange = Utils.getPercentFromTo(it.limit_down, price)

            if (price == it.limit_up && allowUp) { // на верхних
                stockLimit = StockLimit(stock, LimitType.ON_UP, upLimitChange, price, fireTime)
            } else if (price == it.limit_down && allowDown) { // на нижних
                stockLimit = StockLimit(stock, LimitType.ON_DOWN, downLimitChange, price, fireTime)
            } else if (price > it.limit_up && allowUp) { // выше верхних
                stockLimit = StockLimit(stock, LimitType.ABOVE_UP, upLimitChange, price, fireTime)
            } else if (price > center && allowUp) { // близко к верхним
                val percentUpLeft = 100.0 - 100.0 * (stock.getPriceRaw() - center) / (it.limit_up - center)

                if (percentUpLeft < changeUpLimit) { // если близко к лимиту - сигналим!
                    stockLimit = StockLimit(stock, LimitType.NEAR_UP, upLimitChange, price, fireTime)
                } else {

                }

            } else if (price < it.limit_down && allowDown) { // ниже нижних
                stockLimit = StockLimit(stock, LimitType.UNDER_DOWN, downLimitChange, price, fireTime)
            } else if (price < center && allowDown) { // ближе к нижнему
                val percentDownLeft = 100.0 - 100.0 * (center - stock.getPriceRaw()) / (center - it.limit_down)
                if (percentDownLeft < changeDownLimit) { // если близко к лимиту - сигналим!
                    stockLimit = StockLimit(stock, LimitType.NEAR_DOWN, downLimitChange, price, fireTime)
                } else {

                }
            } else {

            }

            if (stockLimit != null) {
                if (stockLimit.type in listOf(LimitType.NEAR_UP, LimitType.ABOVE_UP, LimitType.ON_UP)) {
                    val last = upStockLimits.firstOrNull { stock -> stock.stock.ticker == stock.ticker }
                    if (last != null) {
                        val deltaTime = ((fireTime - last.fireTime) / 60.0 / 1000.0).toInt()
                        if (deltaTime < 5) return
                    }
                    upStockLimits.add(0, stockLimit)
                } else {
                    val last = downStockLimits.firstOrNull { stock -> stock.stock.ticker == stock.ticker }
                    if (last != null) {
                        val deltaTime = ((fireTime - last.fireTime) / 60.0 / 1000.0).toInt()
                        if (deltaTime < 5) return
                    }
                    downStockLimits.add(0, stockLimit)
                }

                GlobalScope.launch(Dispatchers.Main) {
                    strategySpeaker.speakLimit(stockLimit)
                    strategyTelegram.sendLimit(stockLimit)
                    createLimit(stockLimit)
                }
            }
        }

    }

    private fun createLimit(stockLimit: StockLimit) {
        val context: Context = TheApplication.application.applicationContext

        val ticker = stockLimit.ticker
        val notificationChannelId = ticker

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                notificationChannelId,
                "Limits notifications channel $ticker",
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

        val emoji = when (stockLimit.type)  {
            LimitType.ON_UP -> "⬆️ на лимите"
            LimitType.ON_DOWN -> "⬇️️ на лимите"

            LimitType.ABOVE_UP -> "⬆️ выше лимита"
            LimitType.UNDER_DOWN -> "⬇️️ ниже лимита"

            LimitType.NEAR_UP -> "⬆️ рядом с лимитом"
            LimitType.NEAR_DOWN -> "⬇️️ рядом с лимитом"
        }

        val title = "$ticker: $emoji"

        val notification = builder
            .setSubText("$$ticker $emoji")
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