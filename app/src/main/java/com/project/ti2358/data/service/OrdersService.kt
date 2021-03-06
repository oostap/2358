package com.project.ti2358.data.service

import com.project.ti2358.data.api.OrdersApi
import com.project.ti2358.data.model.streamTinkoff.LimitOrderBody
import com.project.ti2358.data.model.streamTinkoff.MarketOrderBody
import com.project.ti2358.data.model.dto.LimitOrder
import com.project.ti2358.data.model.dto.MarketOrder
import com.project.ti2358.data.model.dto.OperationType
import com.project.ti2358.data.model.dto.Order
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