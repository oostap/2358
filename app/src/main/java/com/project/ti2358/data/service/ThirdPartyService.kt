package com.project.ti2358.data.service

import com.project.ti2358.data.api.ThirdPartyApi
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.project.ti2358.data.manager.SettingsManager
import com.project.ti2358.data.model.dto.alor.AlorResponse
import com.project.ti2358.data.model.dto.yahoo.YahooResponse
import com.project.ti2358.service.log
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

    suspend fun alorRefreshToken(@Url url: String): String {
        val urlToken = url + "?token=${SettingsManager.getActiveTokenAlor()}"
        val json = thirdPartyApi.alorRefreshToken(urlToken)
        return json["AccessToken"].toString().replace("\"", "")
    }
}