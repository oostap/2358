package com.project.ti2358.data.service

import com.google.gson.Gson
import com.project.ti2358.data.manager.AlorManager
import com.project.ti2358.data.manager.SettingsManager
import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.model.dto.Candle
import com.project.ti2358.data.model.dto.Interval
import com.project.ti2358.data.model.dto.OrderbookStream
import com.project.ti2358.data.model.streamAlor.BarGetEventBody
import com.project.ti2358.data.model.streamAlor.CancelEventBody
import com.project.ti2358.data.model.streamAlor.OrderBookGetEventBody
import com.project.ti2358.service.Utils
import com.project.ti2358.service.log
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.processors.PublishProcessor
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import org.koin.core.component.KoinApiExtension
import java.util.*
import java.util.concurrent.Executors

@KoinApiExtension
class StreamingAlorService {

    companion object {
        const val STREAMING_URL = "wss://api.alor.ru/ws"
        const val RECONNECT_ATTEMPT_LIMIT = 1000
    }
    
    private var webSocket: WebSocket? = null
    private val client: OkHttpClient = OkHttpClient()
    private val socketListener = AlorSocketListener()
    private var currentAttemptCount = 0
    private val publishProcessor: PublishProcessor<Any> = PublishProcessor.create()
    private val activeCandleSubscriptions: MutableMap<Stock, MutableList<Interval>> = mutableMapOf()
    private val activeOrderSubscriptions: MutableMap<Stock, Int> = mutableMapOf()
    private val threadPoolExecutor = Executors.newSingleThreadExecutor()

    var connectedStatus: Boolean = false
    var messagesStatus: Boolean = false

    init {
        connect()
    }

    fun connect() {
        if (currentAttemptCount > RECONNECT_ATTEMPT_LIMIT) {
            return
        }
        currentAttemptCount++
        val handshakeRequest: Request = Request.Builder()
            .url(STREAMING_URL)
            .build()
        webSocket?.close(1002, null)
        webSocket = client.newWebSocket(
            handshakeRequest,
            socketListener
        )
    }

    fun disconnect() {
        activeCandleSubscriptions.clear()
        webSocket?.close(1002, null)
    }

    inner class AlorSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            log("StreamingAlorService::onOpen")
            resubscribe().subscribe()
            currentAttemptCount = 0
            connectedStatus = true
            messagesStatus = false
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            log("StreamingAlorService::onMessage")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            log("StreamingAlorService::onMessage, text: $text")
            messagesStatus = true

            val jsonObject = JSONObject(text)
            if (jsonObject.has("guid")) {
                val eventType = jsonObject.getString("guid")

                val list = eventType.split("_")
                if (list.size != 2) return

                val ticker = list.first()
                val messageType = list.last()

                val interval: Interval? = Utils.convertStringToInterval(messageType)
                interval?.let {
                    val data = jsonObject.getJSONObject("data")
                    val time = data.getLong("time") * 1000

                    val candle = Candle(
                        data.getDouble("open"), data.getDouble("close"), data.getDouble("high"),
                        data.getDouble("low"), data.getInt("volume"), Date(time), interval, ticker
                    )
                    publishProcessor.onNext(candle)
                    return
                }

                if (messageType == "orderbook") {
                    val data = jsonObject.getJSONObject("data")

                    val jsonBids = data.getJSONArray("bids")
                    val bids = mutableListOf<List<Double>>()
                    for (i in 0 until jsonBids.length()) {
                        val bid = jsonBids.getJSONObject(i)
                        val price = bid.getDouble("price")
                        val quantity = bid.getDouble("volume")
                        bids.add(listOf(price, quantity))
                    }

                    val jsonAsks = data.getJSONArray("asks")
                    val asks = mutableListOf<List<Double>>()
                    for (i in 0 until jsonAsks.length()) {
                        val ask = jsonAsks.getJSONObject(i)
                        val price = ask.getDouble("price")
                        val quantity = ask.getDouble("volume")
                        asks.add(listOf(price, quantity))
                    }
                    val orderbook = OrderbookStream(ticker, 20, bids, asks)
                    publishProcessor.onNext(orderbook)
                    return
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            log("StreamingAlorService::onClosed")
            connectedStatus = false
            messagesStatus = false
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            log("StreamingAlorService::onClosing")
            connectedStatus = false
            messagesStatus = false
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            log("StreamingAlorService::onFailure + ${t.localizedMessage}")
            GlobalScope.launch(Dispatchers.Main) {
                delay(3000)
                connect()
            }
            connectedStatus = false
            messagesStatus = false
        }
    }

    fun resubscribe(): Single<Boolean> {
        return Single.create<Boolean> { emitter ->
            activeCandleSubscriptions.forEach { candleEntry ->
                candleEntry.value.forEach {
                    subscribeBarEventsStream(candleEntry.key, it, addSubscription = false)
                }
            }
            activeOrderSubscriptions.forEach { orderEntry ->
                subscribeOrderBookEventsStream(orderEntry.key, orderEntry.value, addSubscription = false)
            }
            emitter.onSuccess(true)
        }
        .subscribeOn(Schedulers.from(threadPoolExecutor))
    }

    fun getOrderEventStream(stocks: List<Stock>, depth: Int): Flowable<OrderbookStream> {
        return Single.create<Boolean> { emitter ->
            val excessFigis = activeCandleSubscriptions.keys - stocks

            stocks.forEach { stock ->
                if (!isOrderBookSubscribedAlready(stock, depth)) {
                    subscribeOrderBookEventsStream(stock, depth)
                }
            }

            excessFigis.forEach { stock ->
                if (isOrderBookSubscribedAlready(stock, depth)) {
                    unsubscribeOrderBookEventsStream(stock, depth)
                }
            }

            emitter.onSuccess(true)
        }
            .subscribeOn(Schedulers.from(threadPoolExecutor))
            .flatMapPublisher {
                publishProcessor.filter {
                    it is OrderbookStream && it.depth == depth
                } as Flowable<OrderbookStream>
            }
    }

    fun getCandleEventStream(stocks: List<Stock>, interval: Interval): Flowable<Candle> {
        return Single.create<Boolean> { emitter ->
            val excessFigis = activeCandleSubscriptions.keys - stocks

            stocks.forEach { stock ->
                if (!isCandleSubscribedAlready(stock, interval)) {
                    subscribeBarEventsStream(stock, interval)
                }
            }

            excessFigis.forEach { stock ->
                if (isCandleSubscribedAlready(stock, interval)) {
                    unsubscribeCandleEventsStream(stock, interval)
                }
            }

            emitter.onSuccess(true)
        }
        .subscribeOn(Schedulers.from(threadPoolExecutor))
        .flatMapPublisher {
            publishProcessor.filter {
                it is Candle && it.interval == interval
            } as Flowable<Candle>
        }
    }

    private fun isOrderBookSubscribedAlready(stock: Stock, depth: Int): Boolean {
        return activeOrderSubscriptions[stock] == depth
    }

    private fun isCandleSubscribedAlready(stock: Stock, interval: Interval): Boolean {
        return activeCandleSubscriptions[stock]?.contains(interval) ?: false
    }

    private fun subscribeOrderBookEventsStream(stock: Stock, depth: Int, addSubscription: Boolean = true) {
        log("StreamingAlorService :: subscribe for orderbook events: ticker: ${stock.ticker}, depth: $depth")

        val bar = OrderBookGetEventBody(
            AlorManager.TOKEN,
            "OrderBookGetAndSubscribe",
            stock.ticker,
            "SPBX",
            depth,
            "Simple",
            false,
            "${stock.ticker}_orderbook"
        )

        webSocket?.send(Gson().toJson(bar))
        if (addSubscription) {
            activeOrderSubscriptions[stock] = depth
        }
    }

    fun unsubscribeOrderBookEventsStream(stock: Stock, depth: Int) {
        log("StreamingAlorService :: unsubscribe from order book events: ticker: ${stock.ticker}, depth: $depth")
        val cancel = CancelEventBody(SettingsManager.getActiveTokenAlor(), "unsubscribe", "${stock.ticker}_orderbook")
        webSocket?.send(Gson().toJson(cancel))
        activeOrderSubscriptions[stock] = 0
    }

    private fun subscribeBarEventsStream(stock: Stock, interval: Interval, addSubscription: Boolean = true) {
//        log("StreamingAlorService :: subscribe for bars events: ticker: ${stock.marketInstrument.ticker}, interval: $interval")

        val tf = Utils.convertIntervalToAlorTimeframe(interval)
        val timeFrame = Utils.convertIntervalToSeconds(interval)
        val timeName = Utils.convertIntervalToString(interval)

//        val differenceHours = Utils.getTimeDiffBetweenMSK_UTC()
//        val current = Utils.getTimeMSK()
//        current.set(Calendar.HOUR_OF_DAY, 7)
//        current.set(Calendar.MINUTE, 0)
//        current.set(Calendar.SECOND, 0)
//        current.set(Calendar.MILLISECOND, 0)
//        current.add(Calendar.HOUR_OF_DAY, differenceHours)
//        val time = (current.timeInMillis - current.timeZone.rawOffset) / 1000 - 2 * 60 * 60// Calendar.getInstance().timeInMillis / 1000 - 60 * 60 * 24 // сутки, все минутные свечи за сегодня

        val time = Calendar.getInstance().time.time / 1000

        val bar = BarGetEventBody(
            AlorManager.TOKEN,
            "BarsGetAndSubscribe",
            stock.ticker,
            "SPBX",
            tf,
            time,
            "Simple",
            false,
            "${stock.ticker}_$timeName"
        )

        webSocket?.send(Gson().toJson(bar))
        if (addSubscription) {
            if (activeCandleSubscriptions[stock] == null) {
                activeCandleSubscriptions[stock] = mutableListOf(interval)
            } else {
                activeCandleSubscriptions[stock]?.add(interval)
            }
        }
    }

    private fun unsubscribeCandleEventsStream(stock: Stock, interval: Interval) {
        log("StreamingAlorService :: unsubscribe from candle events: ticker: ${stock.ticker}, interval: $interval")
        val timeName = Utils.convertIntervalToString(interval)
        val cancel = CancelEventBody(SettingsManager.getActiveTokenAlor(), "unsubscribe", "${stock.ticker}_$timeName")
        webSocket?.send(Gson().toJson(cancel))
        activeCandleSubscriptions[stock]?.remove(interval)
    }
}