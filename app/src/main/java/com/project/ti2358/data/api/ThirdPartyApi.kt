package com.project.ti2358.data.api

import com.google.gson.JsonObject

import retrofit2.http.GET
import retrofit2.http.Url

interface ThirdPartyApi {
    @GET
    suspend fun yahooPostmarket(@Url url: String): JsonObject
}