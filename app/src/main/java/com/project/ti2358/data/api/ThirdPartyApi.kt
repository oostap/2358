package com.project.ti2358.data.api

import com.google.gson.JsonObject
import com.project.ti2358.data.model.dto.Instrument
import com.project.ti2358.data.model.dto.daager.*
import com.project.ti2358.data.model.response.Response
import retrofit2.http.Body

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

interface ThirdPartyApi {
    @POST
    suspend fun alorRefreshToken(@Url url: String): JsonObject

    @GET
    suspend fun daagerReports(@Url url: String): Map<String, ReportStock>

    @GET
    suspend fun daagerIndices(@Url url: String): List<Index>

    @GET
    suspend fun daagerClosePrice(@Url url: String): Map<String, ClosePrice>

    @GET
    suspend fun daagerShortInfo(@Url url: String): Map<String, StockShort>

    @GET
    suspend fun daagerStockIndices(@Url url: String): StockIndexComponents

    @GET
    suspend fun daagerStock1728(@Url url: String): Response<Map<String, StockPrice1728>>

    @GET
    suspend fun daagerMorningCompanies(@Url url: String): Response<Map<String, Any>>

    @GET
    suspend fun daagerStocks(@Url url: String): Map<String, Instrument>

    @POST
    suspend fun oostapTelegram(@Url url: String, @Body data: JsonObject): JsonObject

    @GET
    suspend fun tinkoffPulse(@Url url: String): Response<Map<String, Any>>

    @GET
    suspend fun githubVersion(@Url url: String): JsonObject
}