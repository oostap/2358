package com.project.ti2358.data.daager.service

import com.google.gson.JsonObject
import com.project.ti2358.data.daager.api.DaagerApi
import com.project.ti2358.data.daager.model.*
import com.project.ti2358.data.manager.SettingsManager
import org.koin.core.component.KoinApiExtension
import retrofit2.Retrofit
import retrofit2.http.Url

class ThirdPartyService(retrofit: Retrofit) {
    private val daagerApi: DaagerApi = retrofit.create(DaagerApi::class.java)

    @KoinApiExtension
    suspend fun alorRefreshToken(@Url url: String): String {
        val urlToken = url + "?token=${SettingsManager.getAlorToken()}"
        val json = daagerApi.alorRefreshToken(urlToken)
        return json["AccessToken"].toString().replace("\"", "")
    }

    /* по вопросам предоставления доступа к запросам писать в телегу: @daager 🤙 */
    suspend fun daagerReports(): Map<String, ReportStock> = daagerApi.daagerReports("https://tinvest.daager.ru/ostap-api/rd_data.json")
    suspend fun daagerIndices(): List<Index> = daagerApi.daagerIndices("https://tinvest.daager.ru/ostap-api/indices.php")
    suspend fun daagerClosePrices(): Map<String, ClosePrice> = daagerApi.daagerClosePrice("https://tinvest.daager.ru/ostap-api/close_alor2.json")
    suspend fun daagerShortInfo(): Map<String, StockShort> = daagerApi.daagerShortInfo("https://tinvest.daager.ru/ostap-api/short.json")
    suspend fun daagerStockIndices(): StockIndexComponents = daagerApi.daagerStockIndices("https://tinvest.daager.ru/ostap-api/gen/indices_components.json")
    suspend fun daagerStock1728(): Map<String, StockPrice1728> = daagerApi.daagerStock1728("https://tinvest.daager.ru/ostap-api/gen/1600.json").payload
    suspend fun daagerMorningCompanies(): Map<String, Any> = daagerApi.daagerMorningCompanies("https://tinvest.daager.ru/ostap-api/gen/morning_companies.json").payload

    suspend fun oostapTelegram(data: JsonObject): JsonObject = daagerApi.oostapTelegram("https://bot.oost.app:2358/", data)

    suspend fun tinkoffPulse(ticker: String): Map<String, Any> = daagerApi.tinkoffPulse("https://api-invest-gw.tinkoff.ru/social/v1/post/instrument/$ticker?limit=30").payload

    suspend fun githubVersion(): String {
        val json = daagerApi.githubVersion("https://api.github.com/repos/oostap/2358/releases/latest")
        return json["tag_name"].toString().replace("\"", "")
    }
}