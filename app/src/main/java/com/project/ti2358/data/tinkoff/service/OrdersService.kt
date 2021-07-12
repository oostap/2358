package com.project.ti2358.data.tinkoff.service

import com.project.ti2358.data.tinkoff.api.OrdersApi
import com.project.ti2358.data.tinkoff.stream.LimitOrderBody
import com.project.ti2358.data.tinkoff.stream.MarketOrderBody
import com.project.ti2358.data.tinkoff.model.LimitOrder
import com.project.ti2358.data.tinkoff.model.MarketOrder
import com.project.ti2358.data.tinkoff.model.OperationType
import com.project.ti2358.data.tinkoff.model.Order
import com.project.ti2358.data.common.BaseService
import retrofit2.Retrofit

class OrdersService(retrofit: Retrofit) : BaseService(retrofit) {
    private val ordersApi: OrdersApi = retrofit.create(OrdersApi::class.java)

    suspend fun placeMarketOrder(lots: Int, figi: String, operation: OperationType, brokerAccountId: String): MarketOrder {
        return ordersApi.placeMarketOrder(
            orderBody = MarketOrderBody(lots = lots, operation = operation),
            figi = figi,
            brokerAccountId = brokerAccountId
        ).payload
    }

    suspend fun placeLimitOrder(lots: Int, figi: String, price: Double, operation: OperationType, brokerAccountId: String): LimitOrder {
        return ordersApi.placeLimitOrder(
            orderBody = LimitOrderBody(lots = lots, price = price, operation = operation),
            figi = figi,
            brokerAccountId = brokerAccountId
        ).payload
    }

    suspend fun orders(brokerAccountId: String): List<Order> {
        return ordersApi.orders(brokerAccountId).payload
    }

    suspend fun cancel(orderId: String, brokerAccountId: String): Any? {
        return ordersApi.cancel(
            orderId = orderId,
            brokerAccountId = brokerAccountId
        ).payload
    }
}