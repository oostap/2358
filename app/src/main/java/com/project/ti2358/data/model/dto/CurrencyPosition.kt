package com.project.ti2358.data.model.dto

data class CurrencyPosition(
    val currency: Currency,
    val balance: Double,
    val blocked: Double,
)