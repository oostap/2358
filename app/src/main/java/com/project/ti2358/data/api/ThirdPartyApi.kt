package com.project.ti2358.data.api

import com.google.gson.JsonObject

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

interface ThirdPartyApi {
    @GET
    suspend fun yahooPostmarket(@Url url: String): JsonObject

    @POST
    suspend fun alorRefreshToken(@Url url: String): JsonObject
}