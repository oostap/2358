package com.project.ti2358.data.service

import com.project.ti2358.data.api.ThirdPartyApi
import com.google.gson.Gson
import com.project.ti2358.data.manager.SettingsManager
import com.project.ti2358.data.model.dto.reports.ClosePrice
import com.project.ti2358.data.model.dto.reports.Index
import com.project.ti2358.data.model.dto.reports.ReportStock
import retrofit2.Retrofit
import retrofit2.http.Url

class ThirdPartyService(
    retrofit: Retrofit
) {
    private val gson = Gson()
    private val thirdPartyApi: ThirdPartyApi = retrofit.create(ThirdPartyApi::class.java)

    suspend fun alorRefreshToken(@Url url: String): String {
        val urlToken = url + "?token=${SettingsManager.getActiveTokenAlor()}"
        val json = thirdPartyApi.alorRefreshToken(urlToken)
        return json["AccessToken"].toString().replace("\"", "")
    }

    suspend fun daagerReports(): Map<String, ReportStock> = thirdPartyApi.daagerReports("https://tinvest.daager.ru/ostap-api/list.json")

    suspend fun daagerIndices(): List<Index> = thirdPartyApi.daagerIndices("https://tinvest.daager.ru/ostap-api/indices.php")

    suspend fun daagerClosePrices(): Map<String, ClosePrice> = thirdPartyApi.daagerClosePrice("https://tinvest.daager.ru/ostap-api/close.json")

    suspend fun githubVersion(): String {
        val json = thirdPartyApi.githubVersion("https://api.github.com/repos/oostap/2358/releases/latest")
        return json["tag_name"].toString().replace("\"", "")
    }
}