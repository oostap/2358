package com.project.ti2358.data.tinkoff.service

import com.project.ti2358.data.tinkoff.api.OperationsApi
import com.project.ti2358.data.common.BaseService
import retrofit2.Retrofit

class OperationsService(retrofit: Retrofit) : BaseService(retrofit) {
    private val operationsApi: OperationsApi = retrofit.create(OperationsApi::class.java)

    suspend fun operations(from: String, to: String, brokerAccountId: String) = operationsApi.operations(from, to, brokerAccountId).payload
}