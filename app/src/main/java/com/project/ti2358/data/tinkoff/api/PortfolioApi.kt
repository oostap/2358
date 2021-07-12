package com.project.ti2358.data.tinkoff.api

import com.project.ti2358.data.tinkoff.model.Accounts
import com.project.ti2358.data.tinkoff.model.Currencies
import com.project.ti2358.data.tinkoff.model.Portfolio
import com.project.ti2358.data.common.Response

import retrofit2.http.GET
import retrofit2.http.Query

interface PortfolioApi {
    @GET("portfolio")
    suspend fun portfolio(@Query("brokerAccountId") brokerAccountId: String): Response<Portfolio>

    @GET("portfolio/currencies")
    suspend fun currencies(@Query("brokerAccountId") brokerAccountId: String): Response<Currencies>

    @GET("user/accounts")
    suspend fun accounts(): Response<Accounts>
}