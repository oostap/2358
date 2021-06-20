package com.project.ti2358.data.service

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.project.ti2358.data.manager.SettingsManager
import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.model.dto.Candle
import com.project.ti2358.data.model.dto.OrderbookStream
import com.project.ti2358.data.model.dto.pantini.PantiniLenta
import com.project.ti2358.data.model.dto.pantini.PantiniOrderbook
import com.project.ti2358.data.model.streamPantini.PantiniAuthBody
import com.project.ti2358.data.model.streamPantini.PantiniMessageBody
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
import org.json.JSONArray
import org.json.JSONObject
import org.koin.core.component.KoinApiExtension
import java.util.*
import java.util.concurrent.Executors

@KoinApiExtension
class StreamingPantiniService {

    companion object {
        const val STREAMING_URL = "wss://nsolid.ru:28973/pantini"
        const val RECONNECT_ATTEMPT_LIMIT = 10000
    }

    private var webSocket: WebSocket? = null
    private val client: OkHttpClient = OkHttpClient()
    private val socketListener = PantiniSocketListener()
    private val gson = Gson()
    private var currentAttemptCount = 0
    private val publishProcessor: PublishProcessor<Any> = PublishProcessor.create()

    private val activeLentaSubscriptions: MutableList<Stock> = Collections.synchronizedList(mutableListOf())
    private val activeOrderbookSubscriptions: MutableList<Stock> = Collections.synchronizedList(mutableListOf())

    private val threadPoolExecutor = Executors.newSingleThreadExecutor()

    var connectedStatus: Boolean = false
    var messagesStatus: Boolean = false

    @KoinApiExtension
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
        activeLentaSubscriptions.clear()
        activeOrderbookSubscriptions.clear()
        webSocket?.close(1002, null)
    }

    fun authorize() {
        webSocket?.send(Gson().toJson(PantiniAuthBody("auth", SettingsManager.getPantiniTelegramID(), SettingsManager.getPantiniWardenToken())))
    }

    inner class PantiniSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            log("StreamingPantiniService::onOpen")
            authorize()
            currentAttemptCount = 0
            connectedStatus = true
            messagesStatus = false
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            log("StreamingPantiniService :: onMessage")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            messagesStatus = true
            log("StreamingPantiniService::onMessage, text: $text")
            val jsonObject = JSONObject(text)
            if (!jsonObject.isNull("m")) {
                val m: String? = jsonObject.getString("m")
                if (m == "auth-success") {
                    currentAttemptCount = 0
                    connectedStatus = true
                    messagesStatus = false
                    resubscribe().subscribe()
                }
                return
            }

            var ticker = ""
            if (!jsonObject.isNull("t")) {
                ticker = jsonObject.getString("t")
            }

            if (!jsonObject.isNull("l2h") && ticker != "") { // стакан {"t":"BBBY","l2h":{"PA":"27.05|100|27.3|4100","Q":"27.11|200|27.48|200","DX":"27.02|200|30.4|500"}}
                val l2h: JSONObject = jsonObject.getJSONObject("l2h")
                val orderbook = PantiniOrderbook(ticker)
                orderbook.create(l2h)
                publishProcessor.onNext(orderbook)
                return
            }

            if (jsonObject.isNull("tsh") && ticker != "") { // лента {"t":"BBBY","tsh":["1624038539527|27.38|@ T |QD|350|1","1624038540152|27.39|@ TI|PA|33|3","1624038540152|27.38|@ TI|PA|1|2","1624038540257|27.38|@FT |PA|115|2","1624038540358|27.38|@FTI|PA|55|2","1624038545177|27.38|@FT |PA|600|2","1624038548565|27.38|@FT |PA|300|2","1624038548687|27.38|@FT |PA|300|2","1624038583507|27.34|@ T |QD|871|1","1624038606161|27.42|@ TI|QD|1|5","1624038613437|27.4|@ TI|QD|50|5","1624038614808|27.37|@ TI|PA|2|5","1624038689008|27.36|@FTI|PA|2|5","1624038714791|27.37|@FTI|PA|10|5","1624038786827|27.36|@ TI|PA|7|5","1624038814359|27.35|@ TI|PA|25|5","1624038827173|27.35|@ TI|Q|1|5","1624038934562|27.36|@ TI|PA|2|5","1624038981078|27.34|@ TI|QD|45|1","1624039050200|27.37|@ TI|PA|3|5","1624039157433|27.37|@ TI|PA|15|5","1624039218743|27.58|@ TI|QD|25|2","1624039306878|27.58|@ TI|QD|10|2","1624039485360|27.49|@ TI|QD|16|5","1624039485360|27.5|@ TI|QD|10|5","1624039786924|27.5|@ T |QD|974|1","1624040098164|27.41|@ TI|PA|5|5","1624040098164|27.4|@ TI|PA|21|5","1624040518486|27.4|@ TI|PA|20|5","1624040624573|27.5|@ TI|QD|1|2","1624041023245|27.4|@FTI|PA|59|5","1624041023245|27.38|@FTI|PA|10|5","1624041023246|27.36|@FTI|Q|2|5","1624041023246|27.34|@FTI|PA|29|1","1624041353271|27.38|@ TI|PA|1|5","1624041353271|27.38|@ TI|PA|4|5","1624041420873|27.48|@ TI|PA|1|5","1624041438246|27.48|@ TI|PA|10|5","1624041568825|27.38|@FTI|PA|1|5","1624041568826|27.35|@FTI|PA|40|5","1624041568827|27.34|@FTI|PA|3|1","1624041596021|27.47|@FTI|PA|10|5","1624041629137|27.47|@ TI|PA|1|5","1624041716153|27.47|@ TI|PA|1|5","1624041757100|27.47|@ TI|PA|1|5","1624042986184|27.37|@ TI|PA|50|5","1624042986184|27.34|@ TI|PA|39|1","1624042993237|27.48|@ T |QD|100|2","1624043018114|27.48|@ T |QD|100|2","1624043219432|27.46|@ TI|PA|3|5","1624043469001|27.48|@ T |QD|100|2","1624043679401|27.45|@ TI|PA|6|5","1624043797530|27.45|@ TI|PA|30|5","1624044252761|27.34|@FTI|PA|50|1","1624044451461|27.4|@ T |PA|200|5","1624044514925|27.34|@FTI|PA|67|5","1624044514925|27.34|@FTI|PA|9|5","1624045153906|27.38|@ TI|PA|50|5","1624045153943|27.38|@ TI|PA|40|5","1624045422499|27.39|@ T |QD|200|2","1624045476370|27.38|@ TI|PA|1|5","1624045676945|27.39|@ TI|QD|50|2","1624045784439|27.34|@ TI|QD|46|5","1624045784439|27.3|@ TI|QD|1|1","1624045804635|27.34|@FTI|Q|12|5","1624045804635|27.34|@FTI|Q|20|5","1624045804635|27.3|@FTI|Q|80|5","1624045804635|27.3|@FTI|Q|10|5","1624045804635|27.27|@FTI|Q|1|5","1624045804635|27.25|@FTI|Q|1|1","1624045804635|27.34|@FTI|PA|1|5","1624045804635|27.32|@FTI|PA|10|5","1624045804635|27.3|@FTI|PA|5|5","1624045804635|27.3|@FTI|PA|1|5","1624045804635|27.3|@FTI|PA|2|5","1624045804635|27.25|@FTI|PA|20|5","1624045804635|27.25|@FTI|PA|75|5","1624045804636|27.2|@FTI|PA|5|5","1624045811907|27.2|@FT |PA|2710|5","1624045859203|27.19|@ TI|PA|10|5","1624045949954|27.25|@ TI|QD|50|5","1624045971355|27.19|@ TI|PA|10|5","1624046006426|27.25|@ TI|QD|10|5","1624046127738|27.29|@ T |PA|300|5","1624046127738|27.3|@ T |PA|2700|2","1624046138985|27.26|@ TI|PA|5|5","1624046138985|27.25|@ TI|PA|5|1","1624046138985|27.25|@ TI|PA|85|1","1624046235510|27.29|@ TI|QD|10|2","1624046270762|27.29|@ TI|QD|15|2","1624046330924|27.29|@FTI|Q|10|5","1624046334508|27.29|@ TI|PA|10|5","1624046335477|27.25|@ TI|PA|15|5","1624046335477|27.25|@ TI|PA|85|5","1624046346781|27.15|@ TI|QD|5|5","1624046346784|27.25|@ TI|PA|10|5","1624046361731|27.25|@ TI|PA|5|1","1624046361731|27.25|@ T |PA|145|1","1624046365348|27.3|@ TI|QD|2|2","1624046383126|27.3|@ T |PA|155|2"]}
                val tsh: JSONArray = jsonObject.getJSONArray("tsh")
                val lenta = PantiniLenta(ticker)
                lenta.create(tsh)
                publishProcessor.onNext(lenta)
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            log("StreamingPantiniService :: onClosed")
            connectedStatus = false
            messagesStatus = false
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            log("StreamingPantiniService :: onClosing")
            connectedStatus = false
            messagesStatus = false
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            log("StreamingPantiniService :: onFailure")
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
                activeOrderbookSubscriptions.forEach {
                    subscribeOrderbookEventsStream(it)
                }

                activeLentaSubscriptions.forEach { it
                    subscribeLentaEventsStream(it)
                }

                emitter.onSuccess(true)
            }
            .subscribeOn(Schedulers.from(threadPoolExecutor))
    }

    fun getOrderbookEventStream(stock: Stock): Flowable<PantiniOrderbook> {
        return Single
            .create<Boolean> { emitter ->
                val excessFigis = activeOrderbookSubscriptions - stock

                if (!isOrderbookSubscribedAlready(stock)) {
                    subscribeOrderbookEventsStream(stock)
                }

                excessFigis.forEach { stock ->
                    if (isOrderbookSubscribedAlready(stock)) {
                        unsubscribeOrderbookEventsStream(stock)
                    }
                }

                emitter.onSuccess(true)
            }
            .subscribeOn(Schedulers.from(threadPoolExecutor))
            .flatMapPublisher { publishProcessor.filter { it is PantiniOrderbook } as Flowable<PantiniOrderbook> }
    }

    fun getLentaEventStream(stock: Stock): Flowable<PantiniLenta> {
        return Single
            .create<Boolean> { emitter ->
                val excessFigis = activeLentaSubscriptions - stock

                if (!isLentaSubscribedAlready(stock)) {
                    subscribeLentaEventsStream(stock)
                }

                excessFigis.forEach { stock ->
                    if (isLentaSubscribedAlready(stock)) {
                        unsubscribeLentaEventsStream(stock)
                    }
                }

                emitter.onSuccess(true)
            }
            .subscribeOn(Schedulers.from(threadPoolExecutor))
            .flatMapPublisher { publishProcessor.filter { it is PantiniLenta } as Flowable<PantiniLenta> }
    }

    private fun isOrderbookSubscribedAlready(stock: Stock): Boolean {
        return activeOrderbookSubscriptions.find { it.ticker == stock.ticker } != null
    }

    private fun isLentaSubscribedAlready(stock: Stock): Boolean {
        return activeLentaSubscriptions.find { it.ticker == stock.ticker } != null
    }

    private fun subscribeOrderbookEventsStream(stock: Stock) {
//        Log.d("StreamingService", "subscribe for order events: figi: $figi, depth: $depth")
        webSocket?.send(Gson().toJson(PantiniMessageBody("l2sub", stock.ticker, true)))
        if (activeOrderbookSubscriptions.find { it.ticker == stock.ticker } == null) {
            activeOrderbookSubscriptions.add(stock)
        }
    }

    fun unsubscribeOrderbookEventsStream(stock: Stock) {
        webSocket?.send(Gson().toJson(PantiniMessageBody("l2unsub", stock.ticker)))
        activeOrderbookSubscriptions.removeAll { it.ticker == stock.ticker }
    }

    private fun subscribeLentaEventsStream(stock: Stock) {
//        Log.d("StreamingService", "subscribe for candle events: figi: $figi, interval: $interval")
        webSocket?.send(Gson().toJson(PantiniMessageBody("tssub", stock.ticker, true)))
        if (activeLentaSubscriptions.find { it.ticker == stock.ticker } == null) {
            activeLentaSubscriptions.add(stock)
        }
    }

    fun unsubscribeLentaEventsStream(stock: Stock) {
//        Log.d("StreamingService", "unsubscribe from candle events: figi: $figi, interval: $interval")
        webSocket?.send(Gson().toJson(PantiniMessageBody("tsunsub", stock.ticker)))
        activeOrderbookSubscriptions.removeAll { it.ticker == stock.ticker }
    }

}