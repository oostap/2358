package com.project.ti2358.data.service

import android.util.Log
import com.google.gson.Gson
import com.project.ti2358.data.manager.AlorManager
import com.project.ti2358.data.manager.SettingsManager
import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.model.streamAlor.BarGetEventBody
import com.project.ti2358.data.model.dto.Candle
import com.project.ti2358.data.model.dto.Interval
import com.project.ti2358.data.model.dto.OrderEvent
import com.project.ti2358.data.model.streamAlor.CancelEventBody
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
    
    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private val client: OkHttpClient = OkHttpClient()
    private val socketListener = AlorSocketListener()
    private var currentAttemptCount = 0
    private val publishProcessor: PublishProcessor<Any> = PublishProcessor.create()
    private val activeBarSubscriptions: MutableMap<Stock, MutableList<Interval>> = mutableMapOf()
    private val threadPoolExecutor = Executors.newSingleThreadExecutor()

    init {
        connect()
    }

    private fun connect() {
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

    inner class AlorSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            log("StreamingAlorService::onOpen")
            resubscribe().subscribe()
            currentAttemptCount = 0
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            log("StreamingAlorService::onMessage")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
//            log("StreamingAlorService::onMessage, text: $text")
            val jsonObject = JSONObject(text)
            if (jsonObject.has("guid")) {
                val eventType = jsonObject.getString("guid")
                if ("1min" in eventType) {
                    if (eventType == "AAPL_1min") {
                        log("StreamingAlorService::onMessage: $jsonObject")
                    }
                }
            }
//            val payload = jsonObject.getString("payload")
//            when (eventType) {
//                "candle" -> {
//                    publishProcessor.onNext(gson.fromJson(payload, Candle::class.java))
//                }
//                "orderbook" -> {
//                    publishProcessor.onNext(gson.fromJson(payload, OrderEvent::class.java))
//                }
//            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            log("StreamingAlorService::onClosed")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            log("StreamingAlorService::onClosing")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            log("StreamingAlorService::onFailure + ${t.localizedMessage}")
            GlobalScope.launch(Dispatchers.Main) {
                delay(3000)
                connect()
            }
        }
    }

    fun resubscribe(): Single<Boolean> {
        return Single
            .create<Boolean> { emitter ->
                activeBarSubscriptions.forEach { candleEntry ->
                    candleEntry.value.forEach {
                        subscribeBarEventsStream(candleEntry.key, it, addSubscription = false)
                    }
                }
                emitter.onSuccess(true)
            }
            .subscribeOn(Schedulers.from(threadPoolExecutor))
    }

    fun getBarEventStream(
        stocks: List<Stock>,
        interval: Interval
    ): Flowable<Candle> {
        return Single
            .create<Boolean> { emitter ->
                val excessFigis = activeBarSubscriptions.keys - stocks

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
    
    private fun isCandleSubscribedAlready(stock: Stock, interval: Interval): Boolean {
        return activeBarSubscriptions[stock]?.contains(interval) ?: false
    }

    private fun subscribeBarEventsStream(stock: Stock, interval: Interval, addSubscription: Boolean = true) {
//        log("StreamingAlorService :: subscribe for bars events: ticker: ${stock.marketInstrument.ticker}, interval: $interval")

        val time = Calendar.getInstance().timeInMillis / 1000 - 60 * 60 * 36
        val bar = BarGetEventBody(
            AlorManager.TOKEN,
            "BarsGetAndSubscribe",
            stock.marketInstrument.ticker,
            "SPBX",
            60,
            time,
            "Simple",
            false,
            "${stock.marketInstrument.ticker}_1min")

        webSocket?.send(Gson().toJson(bar))
        if (addSubscription) {
            if (activeBarSubscriptions[stock] == null) {
                activeBarSubscriptions[stock] = mutableListOf(interval)
            } else {
                activeBarSubscriptions[stock]?.add(interval)
            }
        }
    }

    private fun unsubscribeCandleEventsStream(stock: Stock, interval: Interval) {
        Log.d("StreamingAlorService", "unsubscribe from candle events: ticker: ${stock.marketInstrument.ticker}, interval: $interval")

        val cancel = CancelEventBody(SettingsManager.getActiveTokenAlor(), "unsubscribe", stock.marketInstrument.ticker)
        webSocket?.send(Gson().toJson(cancel))
        activeBarSubscriptions[stock]?.remove(interval)
    }
}