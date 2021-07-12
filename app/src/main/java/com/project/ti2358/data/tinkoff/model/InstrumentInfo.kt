package com.project.ti2358.data.tinkoff.model

data class InstrumentInfo (
    val trade_status: String,
    val min_price_increment: Double,
    val lot: Double,
    val limit_up: Double,
    val limit_down: Double,
    val figi: String,
)