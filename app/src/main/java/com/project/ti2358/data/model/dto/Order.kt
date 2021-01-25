package com.project.ti2358.data.model.dto

data class Order(
    val orderId: String,
    val figi: String,
    val operation: OperationType,
    val status: OrderStatus,
    val requestedLots: Int,
    val executedLots: Int,
    val type: OrderType,
    val price: Double
)