package com.project.ti2358.data.tinkoff.service

import com.project.ti2358.data.tinkoff.api.MarketApi
import com.project.ti2358.data.common.BaseService
import retrofit2.Retrofit

class MarketService(retrofit: Retrofit) : BaseService(retrofit) {
    private val marketApi: MarketApi = retrofit.create(MarketApi::class.java)

    suspend fun stocks() = marketApi.stocks().payload
    suspend fun searchByTicker(ticker: String) = marketApi.searchByTicker(ticker).payload

    suspend fun candles(figi: String, interval: String, from: String, to: String) = marketApi.candles(figi, interval, from, to).payload

    suspend fun orderbook(figi: String, depth: Int) = marketApi.orderbook(figi, depth).payload
}