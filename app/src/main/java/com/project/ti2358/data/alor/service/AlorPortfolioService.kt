package com.project.ti2358.data.alor.service

import com.project.ti2358.data.alor.api.AlorPortfolioApi
import com.project.ti2358.data.alor.model.AlorExchange
import com.project.ti2358.data.tinkoff.api.PortfolioApi
import com.project.ti2358.data.common.BaseService
import retrofit2.Retrofit

class AlorPortfolioService(retrofit: Retrofit) : BaseService(retrofit) {
    private val alorPortfolioApi: AlorPortfolioApi = retrofit.create(AlorPortfolioApi::class.java)

    suspend fun portfolios(username: String) = alorPortfolioApi.portfolios("https://api.alor.ru/client/v1.0/users/${username}/portfolios")

    suspend fun orders(exchange: AlorExchange, portfolio: String) = alorPortfolioApi.orders("https://api.alor.ru/md/v2/clients/${exchange}/${portfolio}/orders")

    suspend fun stoporders(exchange: AlorExchange, portfolio: String) = alorPortfolioApi.stoporders("https://api.alor.ru/md/v2/clients/${exchange}/${portfolio}/stoporders")

    suspend fun money(exchange: AlorExchange, portfolio: String) = alorPortfolioApi.money("https://api.alor.ru/md/v2/clients/legacy/${exchange}/${portfolio}/money")

    suspend fun summary(exchange: AlorExchange, portfolio: String) = alorPortfolioApi.summary("https://api.alor.ru/md/v2/clients/${exchange}/${portfolio}/summary")

    suspend fun positions(exchange: AlorExchange, portfolio: String) = alorPortfolioApi.positions("https://api.alor.ru//md/v2/clients/${exchange}/${portfolio}/positions")

    suspend fun operations(exchange: AlorExchange, portfolio: String) = alorPortfolioApi.operations("https://api.alor.ru/md/v2/clients/${exchange}/${portfolio}/trades")
}