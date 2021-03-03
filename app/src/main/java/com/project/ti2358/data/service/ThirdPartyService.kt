package com.project.ti2358.data.service

import com.project.ti2358.data.api.ThirdPartyApi
import com.project.ti2358.data.model.dto.yahoo.YahooResponse
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import retrofit2.Retrofit
import retrofit2.http.Url

class ThirdPartyService(
    retrofit: Retrofit
) {
    private val gson = Gson()
    private val thirdPartyApi: ThirdPartyApi = retrofit.create(ThirdPartyApi::class.java)

    suspend fun yahooPostmarket(@Url url: String): YahooResponse? {
        val json = thirdPartyApi.yahooPostmarket(url)
        val summary = json["quoteSummary"] as JsonObject
        val result = summary["result"] as JsonArray
        val prices = result[0] as JsonObject
        val price = prices["price"] as JsonObject
        return gson.fromJson(price, YahooResponse::class.java)
    }
}