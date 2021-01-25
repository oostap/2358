package com.project.ti2358.data.service

import com.project.ti2358.data.api.SandboxApi
import com.project.ti2358.data.model.body.SandboxCurrencyBalanceBody
import com.project.ti2358.data.model.body.SandboxRegisterBody
import com.project.ti2358.data.model.dto.Currency
import retrofit2.Retrofit

class SandboxService(retrofit: Retrofit) : BaseService(retrofit) {

    private val sandboxApi: SandboxApi = retrofit.create(SandboxApi::class.java)

    public suspend fun register() = sandboxApi.register(SandboxRegisterBody()).payload

    public suspend fun setCurrencyBalance(
        currency: Currency,
        balance: Int
    ) = sandboxApi.setCurrencyBalance(SandboxCurrencyBalanceBody(currency, balance))

    public suspend fun remove() = sandboxApi.remove()

    public suspend fun clear() = sandboxApi.clear()
}