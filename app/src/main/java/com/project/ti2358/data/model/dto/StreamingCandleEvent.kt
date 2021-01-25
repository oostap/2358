package com.project.ti2358.data.model.dto

data class StreamingCandleEvent(
    val event: String,
    val time: String,
    val payload: Candle
)