package com.project.ti2358.data.service

import com.project.ti2358.data.api.OrdersApi
import com.project.ti2358.data.model.body.LimitOrderBody
import com.project.ti2358.data.model.body.MarketOrderBody
import com.project.ti2358.data.model.dto.LimitOrder
import com.project.ti2358.data.model.dto.MarketOrder
import com.project.ti2358.data.model.dto.OperationType
import com.project.ti2358.data.model.dto.Order
import retrofit2.Retrofit

class OrdersService(retrofit: Retrofit) : BaseService(retrofit) {
    private val ordersApi: OrdersApi = retrofit.create(OrdersApi::class.java)

    suspend fun placeMarketOrder(lots: Int, figi: String, operation: OperationType): MarketOrder {
        return ordersApi.placeMarketOrder(
            orderBody = MarketOrderBody(lots = lots, operation = operation),
            figi = figi
        ).payload
    }

    suspend fun placeLimitOrder(lots: Int, figi: String, price: Double, operation: OperationType): LimitOrder {
        return ordersApi.placeLimitOrder(
            orderBody = LimitOrderBody(lots = lots, price, operation = operation),
            figi = figi
        ).payload
    }

    suspend fun orders(): List<Order> {
        return ordersApi.orders().payload
    }

    suspend fun cancel(orderId: String): Any? {
        return ordersApi.cancel(
            orderId = orderId
        ).payload
    }
}