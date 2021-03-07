package com.project.ti2358.data.service

import android.util.Log
import com.google.gson.Gson
import com.project.ti2358.data.manager.SettingsManager
import com.project.ti2358.data.model.streamTinkoff.OrderEventBody
import com.project.ti2358.data.model.dto.Candle
import com.project.ti2358.data.model.dto.Interval
import com.project.ti2358.data.model.dto.OrderEvent
import com.project.ti2358.data.model.streamTinkoff.CandleEventBody
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
import java.util.concurrent.Executors

class StreamingTinkoffService {

    companion object {
        const val STREAMING_URL = "wss://api-invest.tinkoff.ru/openapi/md/v1/md-openapi/ws"
        const val RECONNECT_ATTEMPT_LIMIT = 1000
    }

    private var webSocket: WebSocket? = null
    private val client: OkHttpClient = OkHttpClient()
    private val socketListener = TinkoffSocketListener()
    private val gson = Gson()
    private var currentAttemptCount = 0
    private val publishProcessor: PublishProcessor<Any> = PublishProcessor.create()
    private val activeCandleSubscriptions: MutableMap<String, MutableList<Interval>> = mutableMapOf()
    private val activeOrderSubscriptions: MutableMap<String, MutableList<Int>> = mutableMapOf()
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
            .addHeader(
                AuthInterceptor.AUTHORIZATION_HEADER,
                AuthInterceptor.BEARER_PREFIX + SettingsManager.getActiveTokenTinkoff()
            )
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

    inner class TinkoffSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            log("StreamingTinkoffService::onOpen")
            resubscribe().subscribe()
            currentAttemptCount = 0
            connectedStatus = true
            messagesStatus = false
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            log("StreamingTinkoffService :: onMessage")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            messagesStatus = true
//            log("StreamingService::onMessage, text: $text")
            val jsonObject = JSONObject(text)
            val eventType = jsonObject.getString("event")
            val payload = jsonObject.getString("payload")
            when (eventType) {
                "candle" -> {
                    publishProcessor.onNext(gson.fromJson(payload, Candle::class.java))
                }
                "orderbook" -> {
                    publishProcessor.onNext(gson.fromJson(payload, OrderEvent::class.java))
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            log("StreamingTinkoffService :: onClosed")
            connectedStatus = false
            messagesStatus = false
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            log("StreamingTinkoffService :: onClosing")
            connectedStatus = false
            messagesStatus = false
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            log("StreamingTinkoffService :: onFailure")
            GlobalScope.launch(Dispatchers.Main) {
                delay(3000)
                connect()
            }
            connectedStatus = false
            messagesStatus = false
        }
    }

    fun resubscribe(): Single<Boolean> {
        return Single
            .create<Boolean> { emitter ->
                activeOrderSubscriptions.forEach { orderEntry ->
                    orderEntry.value.forEach {
                        subscribeOrderEventsStream(orderEntry.key, it, addSubscription = false)
                    }
                }
                activeCandleSubscriptions.forEach { candleEntry ->
                    candleEntry.value.forEach {
                        subscribeCandleEventsStream(candleEntry.key, it, addSubscription = false)
                    }
                }

                emitter.onSuccess(true)
            }
            .subscribeOn(Schedulers.from(threadPoolExecutor))
    }

    fun getOrderEventStream(
        figis: List<String>,
        depth: Int
    ): Flowable<OrderEvent> {
        return Single
            .create<Boolean> { emitter ->
                val excessFigis = activeOrderSubscriptions.keys - figis

                figis.forEach { figi ->
                    if (!isOrderSubscribedAlready(figi, depth)) {
                        subscribeOrderEventsStream(figi, depth)
                    }
                }

                excessFigis.forEach { figi ->
                    if (isOrderSubscribedAlready(figi, depth)) {
                        unsubscribeOrderEventsStream(figi, depth)
                    }
                }

                emitter.onSuccess(true)
            }
            .subscribeOn(Schedulers.from(threadPoolExecutor))
            .flatMapPublisher {
                publishProcessor.filter {
                    it is OrderEvent && it.depth == depth
                } as Flowable<OrderEvent>
            }
    }

    fun getCandleEventStream(
        figis: List<String>,
        interval: Interval
    ): Flowable<Candle> {
        return Single
            .create<Boolean> { emitter ->
                val excessFigis = activeCandleSubscriptions.keys - figis

                figis.forEach { figi ->
                    if (!isCandleSubscribedAlready(figi, interval)) {
                        subscribeCandleEventsStream(figi, interval)
                    }
                }

                excessFigis.forEach { figi ->
                    if (isCandleSubscribedAlready(figi, interval)) {
                        unsubscribeCandleEventsStream(figi, interval)
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

    private fun isOrderSubscribedAlready(figi: String, depth: Int): Boolean {
        return activeOrderSubscriptions[figi]?.let { list ->
            list.contains(depth)
        } ?: false
    }

    private fun isCandleSubscribedAlready(figi: String, interval: Interval): Boolean {
        return activeCandleSubscriptions[figi]?.let { list ->
            list.contains(interval)
        } ?: false
    }

    private fun subscribeOrderEventsStream(figi: String, depth: Int, addSubscription: Boolean = true) {
//        Log.d("StreamingService", "subscribe for order events: figi: $figi, depth: $depth")
        webSocket?.send(Gson().toJson(OrderEventBody("orderbook:subscribe", figi, depth)))
        if (addSubscription) {
            if (activeOrderSubscriptions[figi] == null) {
                activeOrderSubscriptions[figi] = mutableListOf(depth)
            } else {
                activeOrderSubscriptions[figi]?.add(depth)
            }
        }
    }

    private fun unsubscribeOrderEventsStream(figi: String, depth: Int) {
        webSocket?.send(Gson().toJson(OrderEventBody("orderbook:unsubscribe", figi, depth)))
        activeOrderSubscriptions[figi]?.remove(depth)
    }


    private fun subscribeCandleEventsStream(figi: String, interval: Interval, addSubscription: Boolean = true) {
//        Log.d("StreamingService", "subscribe for candle events: figi: $figi, interval: $interval")
        webSocket?.send(Gson().toJson(CandleEventBody("candle:subscribe", figi, interval)))
        if (addSubscription) {
            if (activeCandleSubscriptions[figi] == null) {
                activeCandleSubscriptions[figi] = mutableListOf(interval)
            } else {
                activeCandleSubscriptions[figi]?.add(interval)
            }
        }
    }

    private fun unsubscribeCandleEventsStream(figi: String, interval: Interval) {
//        Log.d("StreamingService", "unsubscribe from candle events: figi: $figi, interval: $interval")
        webSocket?.send(Gson().toJson(CandleEventBody("candle:unsubscribe", figi, interval)))
        activeCandleSubscriptions[figi]?.remove(interval)
    }

}