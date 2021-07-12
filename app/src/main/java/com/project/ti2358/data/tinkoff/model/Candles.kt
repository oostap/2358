package com.project.ti2358.data.tinkoff.model

data class Candles (
    val figi: String,
    val interval: Interval,

    val candles: List<Candle>
)
