package com.project.ti2358.data.alor.api

import com.project.ti2358.data.alor.model.*
import com.project.ti2358.data.alor.model.body.AlorBodyOrder
import retrofit2.http.*

interface AlorOrdersApi {
    @POST
    suspend fun placeMarketOrder(
        @Url url: String,
        @Body body: AlorBodyOrder,
        @Header("X-ALOR-REQID") header: String
    ): AlorResponse

    @POST
    suspend fun placeLimitOrder(
        @Url url: String,
        @Body body: AlorBodyOrder,
        @Header("X-ALOR-REQID") header: String
    ): AlorResponse

    @DELETE
    suspend fun cancelOrder(
        @Url url: String,
        @Query("orderId") orderId: String,
        @Query("account") account: String,
        @Query("portfolio") portfolio: String,
        @Query("exchange") exchange: String,
        @Query("stop") stop: Boolean = false,
        @Query("format") format: String = "Simple",
    ): Any
}