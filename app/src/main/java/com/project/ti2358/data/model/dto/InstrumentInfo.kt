package com.project.ti2358.data.model.dto

data class InstrumentInfo (
    val trade_status: String,
    val min_price_increment: Double,
    val lot: Double,
    val limit_up: Double,
    val limit_down: Double,
    val figi: String,
)