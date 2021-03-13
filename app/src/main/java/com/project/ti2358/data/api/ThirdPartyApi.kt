package com.project.ti2358.data.api

import com.google.gson.JsonObject
import com.project.ti2358.data.model.dto.Instrument
import com.project.ti2358.data.model.dto.reports.ClosePrice
import com.project.ti2358.data.model.dto.reports.Index
import com.project.ti2358.data.model.dto.reports.ReportStock

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
    suspend fun daagerStocks(@Url url: String): Map<String, Instrument>

    @GET
    suspend fun githubVersion(@Url url: String): JsonObject
}