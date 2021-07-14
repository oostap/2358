package com.project.ti2358.data.alor.service

import com.project.ti2358.data.alor.api.AlorOrdersApi
import com.project.ti2358.data.alor.api.AlorPortfolioApi
import com.project.ti2358.data.alor.model.AlorExchange
import com.project.ti2358.data.alor.model.AlorOrderSide
import com.project.ti2358.data.alor.model.AlorResponse
import com.project.ti2358.data.alor.model.body.AlorBodyOrder
import com.project.ti2358.data.tinkoff.api.PortfolioApi
import com.project.ti2358.data.common.BaseService
import com.project.ti2358.data.tinkoff.model.MarketOrder
import com.project.ti2358.data.tinkoff.model.OperationType
import com.project.ti2358.data.tinkoff.model.Order
import com.project.ti2358.data.tinkoff.stream.MarketOrderBody
import retrofit2.Retrofit

class AlorOrdersService(retrofit: Retrofit) : BaseService(retrofit) {
    private val alorOrdersApi: AlorOrdersApi = retrofit.create(AlorOrdersApi::class.java)

//    suspend fun placeMarketOrder(lost: Int, ticker: String, operation: OperationType, brokerAccountId: String): AlorResponse {
//
//        val body = AlorBodyOrder()
//
//        return alorOrdersApi.placeMarketOrder(
//            "https://api.alor.ru/commandapi/warptrans/TRADE/v2/client/orders/actions/market,
//            orderBody = MarketOrderBody(lots = lots, operation = operation),
//            figi = figi,
//            brokerAccountId = brokerAccountId
//        ).payload
//    }

//    suspend fun placeLimitOrder(lots: Int, figi: String, operation: OperationType, brokerAccountId: String): MarketOrder {
//        return ordersApi.placeMarketOrder(
//            orderBody = MarketOrderBody(lots = lots, operation = operation),
//            figi = figi,
//            brokerAccountId = brokerAccountId
//        ).payload
//    }
//
//    suspend fun orders(brokerAccountId: String): List<Order> {
//        return ordersApi.orders(brokerAccountId).payload
//    }
//
//    suspend fun cancel(orderId: String, brokerAccountId: String): Any? {
//        return ordersApi.cancel(
//            orderId = orderId,
//            brokerAccountId = brokerAccountId
//        ).payload
//    }

//    suspend fun portfolios(username: String) = alorOrdersApi.portfolios("https://api.alor.ru/client/v1.0/users/${username}/portfolios")
//
//    suspend fun orders(exchange: AlorExchange, portfolio: String) = alorOrdersApi.orders("https://api.alor.ru/md/v2/clients/${exchange}/${portfolio}/orders")
}