package com.project.ti2358.data.tinkoff.model

import com.project.ti2358.data.manager.Stock

data class Order(
    val orderId: String,
    val figi: String,
    val operation: OperationType,
    val status: OrderStatus,
    val requestedLots: Int,
    val executedLots: Int,
    val type: OrderType,
    val price: Double,

    var stock: Stock?
) {

    fun getOperationStatusString(): String {
        if (operation == OperationType.BUY) {
            return "Покупка"
        } else if (operation == OperationType.SELL) {
            return "Продажа"
        }

        return ""
    }
}