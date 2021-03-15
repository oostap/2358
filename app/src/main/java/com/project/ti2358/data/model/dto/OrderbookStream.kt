package com.project.ti2358.data.model.dto

data class OrderbookStream(
    val figi: String,
    val depth: Int,
    val bids: List<List<Double>>,
    val asks: List<List<Double>>
)