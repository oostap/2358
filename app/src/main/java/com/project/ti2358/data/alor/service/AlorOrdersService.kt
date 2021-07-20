package com.project.ti2358.data.alor.service

import com.project.ti2358.data.alor.api.AlorOrdersApi
import com.project.ti2358.data.alor.model.AlorExchange
import com.project.ti2358.data.alor.model.AlorOrderType
import com.project.ti2358.data.alor.model.AlorResponse
import com.project.ti2358.data.alor.model.body.AlorBodyOrder
import com.project.ti2358.data.alor.model.body.AlorInstrument
import com.project.ti2358.data.alor.model.body.AlorUser
import com.project.ti2358.data.common.BaseService
import retrofit2.Retrofit
import com.google.gson.Gson
import com.project.ti2358.data.manager.AlorPortfolioManager
import com.project.ti2358.data.tinkoff.model.OperationType
import com.project.ti2358.service.log
import org.koin.core.component.KoinApiExtension
import kotlin.random.Random.Default.nextLong

class AlorOrdersService(retrofit: Retrofit) : BaseService(retrofit) {
    private val alorOrdersApi: AlorOrdersApi = retrofit.create(AlorOrdersApi::class.java)

    @KoinApiExtension
    suspend fun placeMarketOrder(operation: OperationType, lots: Int, ticker: String, exchange: AlorExchange): AlorResponse {
        val instrument = AlorInstrument(ticker, exchange)
        val user = AlorUser(AlorPortfolioManager.ACCOUNT, AlorPortfolioManager.PORTFOLIO)
        val body = AlorBodyOrder(operation, lots, instrument, user, AlorOrderType.Market)

        val uid = nextLong(1000000000, 10000000000)
        val header = "${AlorPortfolioManager.PORTFOLIO};$uid"
        return alorOrdersApi.placeMarketOrder(
            "https://api.alor.ru/commandapi/warptrans/TRADE/v2/client/orders/actions/market",
            body,
            header
        )
    }

    @KoinApiExtension
    suspend fun placeLimitOrder(operation: OperationType, lots: Int, price: Double, ticker: String, exchange: AlorExchange): AlorResponse {
        val instrument = AlorInstrument(ticker, exchange)
        val user = AlorUser(AlorPortfolioManager.ACCOUNT, AlorPortfolioManager.PORTFOLIO)
        val body = AlorBodyOrder(operation, lots, instrument, user, AlorOrderType.Limit, price)

        val uid = nextLong(1000000000, 10000000000)
        val header = "${AlorPortfolioManager.PORTFOLIO};$uid"

        val bodyString = Gson().toJson(body)
        log("ALOR bodyString = $bodyString")
        return alorOrdersApi.placeLimitOrder(
            "https://api.alor.ru/commandapi/warptrans/TRADE/v2/client/orders/actions/limit",
            body,
            header
        )
    }

    @KoinApiExtension
    suspend fun cancel(orderId: String, exchange: AlorExchange): Any {
        return alorOrdersApi.cancelOrder(
            "https://api.alor.ru/commandapi/warptrans/TRADE/v2/client/orders/${orderId}",
            orderId = orderId,
            account = AlorPortfolioManager.ACCOUNT,
            portfolio = AlorPortfolioManager.PORTFOLIO,
            exchange = exchange.toString()
        )
    }
}