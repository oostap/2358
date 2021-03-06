package com.project.ti2358.data.api

import com.project.ti2358.data.model.dto.Accounts
import com.project.ti2358.data.model.dto.Currencies
import com.project.ti2358.data.model.dto.Portfolio
import com.project.ti2358.data.model.response.Response

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