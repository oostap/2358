package com.project.ti2358.data.tinkoff.api

import com.project.ti2358.data.tinkoff.stream.LimitOrderBody
import com.project.ti2358.data.tinkoff.stream.MarketOrderBody
import com.project.ti2358.data.tinkoff.model.LimitOrder
import com.project.ti2358.data.tinkoff.model.MarketOrder
import com.project.ti2358.data.tinkoff.model.Order
import com.project.ti2358.data.common.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface OrdersApi {
    @POST("orders/market-order")
    suspend fun placeMarketOrder(
        @Body orderBody: MarketOrderBody,
        @Query("figi") figi: String,
        @Query("brokerAccountId") brokerAccountId: String
    ): Response<MarketOrder>

    @POST("orders/limit-order")
    suspend fun placeLimitOrder(
        @Body orderBody: LimitOrderBody,
        @Query("figi") figi: String,
        @Query("brokerAccountId") brokerAccountId: String
    ): Response<LimitOrder>

    @GET("orders")
    suspend fun orders(
        @Query("brokerAccountId") brokerAccountId: String
    ): Response<List<Order>>

    @POST("orders/cancel")
    suspend fun cancel(
        @Query("orderId") orderId: String,
        @Query("brokerAccountId") brokerAccountId: String
    ): Response<Any?>
}