package com.project.ti2358.data.service

import com.project.ti2358.data.api.OperationsApi
import retrofit2.Retrofit

class OperationsService(retrofit: Retrofit) : BaseService(retrofit) {
    private val operationsApi: OperationsApi = retrofit.create(OperationsApi::class.java)

    suspend fun operations(from: String, to: String) = operationsApi.candles(from, to).payload
}