package com.project.ti2358.data.model.dto

data class MarketInstrument (
    val figi: String,
    val ticker: String,
    val isin: String,
    val name: String,

    val minPriceIncrement: Double,
    val lot: Int,
    val minQuantity: Int,
    val currency: Currency,
    val type: InstrumentType
)