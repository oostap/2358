package com.project.ti2358.data.alor.api

import com.project.ti2358.data.alor.model.*

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url

interface AlorOrdersApi {
    @GET
    suspend fun createMarketOrder(@Url url: String, @Header("X-ALOR-REQID") header: String): AlorResponse

}