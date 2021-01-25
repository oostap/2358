package com.project.ti2358.data.api

import com.project.ti2358.data.model.dto.Currencies
import com.project.ti2358.data.model.dto.Portfolio
import com.project.ti2358.data.model.response.Response

import retrofit2.http.GET
import retrofit2.http.Query

interface PortfolioApi {
    @GET("portfolio")
    suspend fun portfolio(): Response<Portfolio>

    @GET("portfolio/currencies")
    suspend fun currencies(): Response<Currencies>
}