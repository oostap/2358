package com.project.ti2358.data.model.dto

class MoneyAmount (
    val currency: Currency,
    val value: Double,
) {
    override fun toString(): String {
        return "$value $currency"
    }
}