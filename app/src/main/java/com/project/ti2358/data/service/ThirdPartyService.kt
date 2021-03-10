package com.project.ti2358.data.service

import com.project.ti2358.data.api.ThirdPartyApi
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.project.ti2358.data.manager.SettingsManager
import com.project.ti2358.data.model.dto.reports.ReportStock
import com.project.ti2358.data.model.dto.yahoo.YahooResponse
import retrofit2.Retrofit
import retrofit2.http.Url

class ThirdPartyService(
    retrofit: Retrofit
) {
    private val gson = Gson()
    private val thirdPartyApi: ThirdPartyApi = retrofit.create(ThirdPartyApi::class.java)

    suspend fun yahooPostmarket(ticker: String): YahooResponse? {
        val url = "https://query1.finance.yahoo.com/v10/finance/quoteSummary/${ticker}?modules=price"
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

    suspend fun daagerReports(): Map<String, ReportStock> = thirdPartyApi.daagerReports("https://tinvest.daager.ru/ostap-api/list.json")
}