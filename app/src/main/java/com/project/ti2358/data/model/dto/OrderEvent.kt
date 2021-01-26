package com.project.ti2358.data.model.dto

data class OrderEvent(
    val figi: String,
    val depth: Int,
    val bids: Array<Array<Double>>,
    val asks: Array<Array<Double>>
)