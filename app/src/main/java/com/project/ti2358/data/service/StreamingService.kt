package com.project.ti2358.data.service

import com.google.gson.Gson
import com.project.ti2358.data.model.body.CandleEventBody
import com.project.ti2358.data.model.body.OrderEventBody
import com.project.ti2358.data.model.dto.Candle
import com.project.ti2358.data.model.dto.Interval
import com.project.ti2358.data.model.dto.OrderEvent
import com.project.ti2358.service.log
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.processors.PublishProcessor
import io.reactivex.rxjava3.schedulers.Schedulers
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.Collections.synchronizedList
import java.util.Collections.synchronizedMap

class StreamingService {

    companion object {
        const val STREAMING_URL = "wss://api-invest.tinkoff.ru/openapi/md/v1/md-openapi/ws"
    }

    private val webSocket: WebSocket
    private val publishProcessor: PublishProcessor<Any> = PublishProcessor.create()
    private val gson = Gson()
    private val activeCandleSubscriptions: MutableMap<String, MutableList<Interval>> = synchronizedMap(mutableMapOf())
    private val activeOrderSubscriptions: MutableMap<String, MutableList<Int>> = synchronizedMap(mutableMapOf())

    init {
        val client = OkHttpClient()
        val handshakeRequest: Request = Request.Builder()
            .url(STREAMING_URL)
            .addHeader(
                AuthInterceptor.AUTHORIZATION_HEADER,
                AuthInterceptor.BEARER_PREFIX + SettingsManager.getActiveToken()
            )
            .build()
        webSocket = client.newWebSocket(
            handshakeRequest,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
//                    Log.v("StreamingService", "onMessage, text: $text")
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
                    log("SOCKET onClosed ${code} ${reason}")
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    log("SOCKET onClosing ${code} ${reason}")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    log("SOCKET onFailure ${response}")
                }
            }
        )
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
            .subscribeOn(Schedulers.io())
            .flatMapPublisher {
                publishProcessor.filter{
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
            .subscribeOn(Schedulers.io())
            .flatMapPublisher {
                publishProcessor.filter{
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

    private fun subscribeOrderEventsStream(figi: String, depth: Int) {
//        Log.d("StreamingService", "subscribe for order events: figi: $figi, depth: $depth")
        webSocket.send(Gson().toJson(OrderEventBody("orderbook:subscribe", figi, depth)))
        if (activeOrderSubscriptions[figi] == null) {
            activeOrderSubscriptions[figi] = mutableListOf(depth)
        } else {
            activeOrderSubscriptions[figi]?.add(depth)
        }
    }

    private fun unsubscribeOrderEventsStream(figi: String, depth: Int) {
        webSocket.send(Gson().toJson(OrderEventBody("orderbook:unsubscribe", figi, depth)))
        activeOrderSubscriptions[figi]?.remove(depth)
    }


    private fun subscribeCandleEventsStream(figi: String, interval: Interval) {
//        Log.d("StreamingService", "subscribe for candle events: figi: $figi, interval: $interval")
        webSocket.send(Gson().toJson(CandleEventBody("candle:subscribe", figi, interval)))
        if (activeCandleSubscriptions[figi] == null) {
            activeCandleSubscriptions[figi] = mutableListOf(interval)
        } else {
            activeCandleSubscriptions[figi]?.add(interval)
        }
    }

    private fun unsubscribeCandleEventsStream(figi: String, interval: Interval) {
//        Log.d("StreamingService", "unsubscribe from candle events: figi: $figi, interval: $interval")
        webSocket.send(Gson().toJson(CandleEventBody("candle:unsubscribe", figi, interval)))
        activeCandleSubscriptions[figi]?.remove(interval)
    }

}