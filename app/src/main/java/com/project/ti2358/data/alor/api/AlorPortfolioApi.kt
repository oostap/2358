package com.project.ti2358.data.alor.api

import com.project.ti2358.data.alor.model.*

import retrofit2.http.GET
import retrofit2.http.Url

interface AlorPortfolioApi {
    @GET
    suspend fun portfolios(@Url url: String): Map<String, List<AlorTradeServer>>

    @GET
    suspend fun orders(@Url url: String): List<AlorOrder>

    @GET
    suspend fun stoporders(@Url url: String): List<AlorOrder>

    @GET
    suspend fun money(@Url url: String): AlorMoney

    @GET
    suspend fun summary(@Url url: String): AlorSummary

    @GET
    suspend fun positions(@Url url: String): List<AlorPosition>
}