package com.project.ti2358.data.service

import android.util.Log
import com.google.gson.Gson
import com.project.ti2358.data.model.body.CandleEventBody
import com.project.ti2358.data.model.dto.Interval
import com.project.ti2358.data.model.dto.StreamingCandleEvent
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.processors.PublishProcessor
import io.reactivex.rxjava3.schedulers.Schedulers
import okhttp3.*
import okio.ByteString

class StreamingService {

    companion object {
        const val STREAMING_URL = "wss://api-invest.tinkoff.ru/openapi/md/v1/md-openapi/ws"
    }

    private val webSocket: WebSocket
    private val publishProcessor: PublishProcessor<StreamingCandleEvent> = PublishProcessor.create()
    private val gson = Gson()
    private val activeSubscriptions: MutableList<String> = mutableListOf()

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
                    Log.d("WebSocket", "onOpen")
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
//                    Log.d("WebSocket", "onMessage, bytes")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
//                    Log.d("WebSocket", "onMessage, text: $text")
                    publishProcessor.onNext(gson.fromJson(text, StreamingCandleEvent::class.java))
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d("WebSocket", "onClosed")
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d("WebSocket", "onClosing")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.d("WebSocket", "onFailure")
                    publishProcessor.onError(t)
                }
            }
        )
    }

    fun getCandleEventsStream(
        figis: List<String>
    ): Flowable<StreamingCandleEvent> {
        return Single
            .create<Boolean> { emitter ->

                val removeList = activeSubscriptions - figis
                val addList = figis - activeSubscriptions

                removeList.forEach{
                    unSubscribeForCandleEvents(it)
                }

                addList.forEach {
                    subscribeForCandleEventsStream(it)
                }
                emitter.onSuccess(true)
            }
            .subscribeOn(Schedulers.io())
            .flatMapPublisher { publishProcessor }
    }

    private fun subscribeForCandleEventsStream(figi: String) {
        webSocket.send(Gson().toJson(CandleEventBody("candle:subscribe", figi, Interval.DAY)))
        activeSubscriptions.add(figi)
    }

    private fun unSubscribeForCandleEvents(figi: String) {
        webSocket.send(Gson().toJson(CandleEventBody("candle:unsubscribe", figi, Interval.DAY)))
        activeSubscriptions.remove(figi)
    }
}