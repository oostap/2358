package com.project.ti2358.data.tinkoff.api

import com.project.ti2358.data.tinkoff.model.Operations
import com.project.ti2358.data.common.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface OperationsApi {
    @GET("operations")
    suspend fun operations(
        @Query("from") from: String,            // 2019-08-19T18:38:33.131642+03:00
        @Query("to") to: String,                // 2019-08-19T18:38:33.131642+03:00
        @Query("brokerAccountId") brokerAccountId: String
    ): Response<Operations>
}