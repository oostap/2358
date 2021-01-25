package com.project.ti2358.data.model.body

import com.project.ti2358.data.model.dto.Currency

data class SandboxCurrencyBalanceBody(
    val currency: Currency,
    val balance: Int
)