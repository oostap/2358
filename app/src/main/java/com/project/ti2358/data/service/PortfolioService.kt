package com.project.ti2358.data.service

import com.project.ti2358.data.api.PortfolioApi
import retrofit2.Retrofit

class PortfolioService(retrofit: Retrofit) : BaseService(retrofit) {
    private val portfolioApi: PortfolioApi = retrofit.create(PortfolioApi::class.java)

    suspend fun portfolio() = portfolioApi.portfolio().payload

    suspend fun currencies() = portfolioApi.currencies().payload
}