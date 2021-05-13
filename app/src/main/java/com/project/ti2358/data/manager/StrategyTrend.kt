package com.project.ti2358.data.manager

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import com.project.ti2358.MainActivity
import com.project.ti2358.R
import com.project.ti2358.TheApplication
import com.project.ti2358.data.model.dto.Candle
import com.project.ti2358.service.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*
import kotlin.math.abs

@KoinApiExtension
class StrategyTrend : KoinComponent {
    private val stockManager: StockManager by inject()
    private val strategySpeaker: StrategySpeaker by inject()
    private val strategyTelegram: StrategyTelegram by inject()

    private var stocks: MutableList<Stock> = mutableListOf()
    var trendUpStocks: MutableList<TrendStock> = mutableListOf()
    var trendDownStocks: MutableList<TrendStock> = mutableListOf()

    private var started: Boolean = false

    fun process(): MutableList<Stock> {
        val all = stockManager.getWhiteStocks()
        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        stocks.clear()
        stocks.addAll(all.filter { it.getPriceNow() > min && it.getPriceNow() < max })

        return stocks
    }

    fun startStrategy() {
        trendUpStocks.clear()
        trendDownStocks.clear()

        process()
        stocks.forEach {
            it.resetTrendPrice()
        }

        started = true

        strategyTelegram.sendTrendStart(true)
    }

    fun stopStrategy() {
        started = false
        strategyTelegram.sendTrendStart(false)
    }

    @Synchronized
    fun processStrategy(stock: Stock, candle: Candle) {
        if (!started) return
        if (stock !in stocks) return

        if (SettingsManager.getTrendLove()) {
            if (StrategyFavorites.stocksSelected.find { it.ticker == stock.ticker } == null) return
        }

        val changeStartPercent = SettingsManager.getTrendMinDownPercent()
        val changeEndPercent = SettingsManager.getTrendMinUpPercent()
        val afterMinutes = SettingsManager.getTrendAfterMinutes()

        // проверка просадки - просто узнать что за тренд
        val changeFromStart = candle.closingPrice / stock.priceTrend * 100.0 - 100.0

        // проверка по времени
        var turnCandleIndex = 0
        var extremumValue = 0.0

        // раскидаем свечки, чтобы было понятней че происходит
        val fromStartToNowCandles: MutableList<Candle> = mutableListOf()
        val fromStartToLowCandles: MutableList<Candle> = mutableListOf()
        val fromLowToNowCandles: MutableList<Candle> = mutableListOf()

        if (stock.minuteCandles.isNotEmpty()) {
            stock.minuteCandles.forEach {
                if (it.time.time >= stock.trendStartTime.time.time) {
                    fromStartToNowCandles.add(it)
                }
            }

            // удаляем последнюю свечу, её не рассматриваем потому что ещё не закрылась
            if (fromStartToNowCandles.isNotEmpty())
                fromStartToNowCandles.removeLast()

            // если прошло мало минут, то игнорим
            if (fromStartToNowCandles.size < afterMinutes) return

            if (changeFromStart < 0) { // если изменение от старта < 0, то ищем минимальную свечу
                extremumValue = 10000.0
                for ((index, value) in fromStartToNowCandles.withIndex()) {
                    if (value.lowestPrice < extremumValue) {
                        extremumValue = value.lowestPrice
                        turnCandleIndex = index
                    }
                }
            } else { // иначе ищем максимальную свечу
                extremumValue = 0.0
                for ((index, value) in fromStartToNowCandles.withIndex()) {
                    if (value.highestPrice > extremumValue) {
                        extremumValue = value.highestPrice
                        turnCandleIndex = index
                    }
                }
            }

            for ((index, value) in fromStartToNowCandles.withIndex()) {
                if (index <= turnCandleIndex) {
                    fromStartToLowCandles.add(value)
                } else {
                    fromLowToNowCandles.add(value)
                }
            }
        }

        // если свечей мало, ливаем
        if (fromStartToLowCandles.isEmpty() || fromLowToNowCandles.isEmpty()) return

        // изменение от старта скана до точки минимума
        val changeFromStartToLow = extremumValue / stock.priceTrend * 100.0 - 100.0

        // изменение от минимума до последней закрытой свечи
        val changeFromLowToNow = fromLowToNowCandles.last().closingPrice / extremumValue * 100.0 - 100.0

        // проверка просадки, большая ли
        if (abs(changeFromStartToLow) < abs(changeStartPercent)) return

        // вычисление процента отскока
        val turnValue = abs(changeFromLowToNow / changeFromStartToLow * 100.0)

        if (abs(turnValue) < changeEndPercent) return

        log("СМЕНА ТРЕНДА ${stock.ticker} = $changeFromStartToLow -> $changeFromLowToNow, total = $changeStartPercent, turnout = $turnValue")

        // если бумага недавно уже отскакивала за последние 5 минут, то не дублировать
        if (trendUpStocks.find { it.stock.ticker == stock.ticker && ((Calendar.getInstance().time.time - it.fireTime) / 60.0 / 1000.0).toInt() < 5 } != null) return
        if (trendDownStocks.find { it.stock.ticker == stock.ticker && ((Calendar.getInstance().time.time - it.fireTime) / 60.0 / 1000.0).toInt() < 5 } != null) return

        val trendStock = TrendStock(stock,
            stock.priceTrend, extremumValue, fromLowToNowCandles.last().closingPrice,
            changeFromStartToLow, changeFromLowToNow, turnValue,
            fromStartToLowCandles.size, fromLowToNowCandles.size,
            fromLowToNowCandles.last().time.time
        )

        if (changeFromStart > 0) {
            trendDownStocks.add(0, trendStock)
        } else {
            trendUpStocks.add(0, trendStock)
        }
        createTrend(trendStock)

        stock.resetTrendPrice()
    }

    private fun createTrend(trendStock: TrendStock) {
        val context: Context = TheApplication.application.applicationContext

        val ticker = trendStock.ticker
        val notificationChannelId = ticker

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                notificationChannelId,
                "Trend notifications channel $ticker",
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

        val changePercent = if (trendStock.changeFromStartToLow > 0) {
            "+%.2f%%".format(locale = Locale.US, trendStock.changeFromStartToLow)
        } else {
            "%.2f%%".format(locale = Locale.US, trendStock.changeFromStartToLow)
        }

        strategySpeaker.speakTrend(trendStock)
        strategyTelegram.sendTrend(trendStock)

        val emoji = if (trendStock.changeFromStartToLow < 0) "⤴️" else "⤵️️"
        val text = "%s$%s %.2f%% - %.2f$ -> %.2f$ = %.2f%%, %.2f$ -> %.2f$ = %.2f%%, %d мин -> %d мин".format(locale = Locale.US,
            emoji,
            trendStock.ticker,
            trendStock.turnValue,
            trendStock.priceStart, trendStock.priceLow, trendStock.changeFromStartToLow,
            trendStock.priceLow, trendStock.priceNow, trendStock.changeFromLowToNow,
            trendStock.timeFromStartToLow, trendStock.timeFromLowToNow)

        val title = text

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
        val uid = kotlin.random.Random.Default.nextInt(0, 100000)
        manager.notify(ticker, uid, notification)

        val alive: Long = SettingsManager.getRocketNotifyAlive().toLong()
        GlobalScope.launch(Dispatchers.Main) {
            delay(1000 * alive)
            manager.cancel(ticker, uid)
        }
    }
}