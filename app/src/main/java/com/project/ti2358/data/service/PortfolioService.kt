package com.project.ti2358.data.service

import com.project.ti2358.data.api.PortfolioApi
import retrofit2.Retrofit

class PortfolioService(retrofit: Retrofit) : BaseService(retrofit) {
    private val portfolioApi: PortfolioApi = retrofit.create(PortfolioApi::class.java)

    suspend fun portfolio(brokerId: String) = portfolioApi.portfolio(brokerId).payload

    suspend fun currencies(brokerId: String) = portfolioApi.currencies(brokerId).payload

    suspend fun accounts() = portfolioApi.accounts().payload
}