package com.project.ti2358.data.service

import com.project.ti2358.data.api.MarketApi
import com.project.ti2358.data.model.dto.Interval
import retrofit2.Retrofit

class MarketService(retrofit: Retrofit) : BaseService(retrofit) {
    private val marketApi: MarketApi = retrofit.create(MarketApi::class.java)

    suspend fun stocks() = marketApi.stocks().payload
    suspend fun searchByTicker(ticker: String) = marketApi.searchByTicker(ticker).payload

    suspend fun candles(figi: String, interval: Interval, from: String, to: String) = marketApi.candles(figi, interval, from, to).payload
}