package com.project.ti2358.data.api

import com.project.ti2358.data.model.body.SandboxCurrencyBalanceBody
import com.project.ti2358.data.model.body.SandboxRegisterBody
import com.project.ti2358.data.model.dto.SandboxAccountInfo
import com.project.ti2358.data.model.response.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface SandboxApi {
    @POST("sandbox/register")
    suspend fun register(@Body registerBody: SandboxRegisterBody): Response<SandboxAccountInfo>

    @POST("sandbox/currencies/balance")
    suspend fun setCurrencyBalance(
        @Body body: SandboxCurrencyBalanceBody,
        @Query("brokerAccountId") brokerAccountId: String? = null
    ): Response<Any?>

    @POST("sandbox/remove")
    suspend fun remove(): Response<Any?>

    @POST("sandbox/clear")
    suspend fun clear(): Response<Any?>
}