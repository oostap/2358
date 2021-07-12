package com.project.ti2358.data.tinkoff.model

import com.project.ti2358.data.tinkoff.model.Currency

class MoneyAmount (
    val currency: Currency,
    val value: Double,
) {
    override fun toString(): String {
        return "$value $currency"
    }
}