package com.project.ti2358.data.tinkoff.model

data class Instrument (
    val name: String,
    val ticker: String,
    val figi: String,
    val isin: String,
    val sector: String,
    val sector_ru: String?,
    val country: String,
    val logo: String,

    val minPriceIncrement: Double,
    val lot: Int,
    val minQuantity: Int,

    val currency: Currency?,
)