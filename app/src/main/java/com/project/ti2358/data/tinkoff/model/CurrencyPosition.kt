package com.project.ti2358.data.tinkoff.model

data class CurrencyPosition(
    val currency: Currency,
    val balance: Double,
    val blocked: Double,
)