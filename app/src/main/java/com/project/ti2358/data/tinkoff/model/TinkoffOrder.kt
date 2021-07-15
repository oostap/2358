package com.project.ti2358.data.tinkoff.model

import com.project.ti2358.data.common.BaseOrder
import com.project.ti2358.data.manager.Stock
import com.project.ti2358.service.Utils

data class TinkoffOrder(
    val orderId: String,
    val figi: String,
    val operation: OperationType,
    val status: OrderStatus,
    val requestedLots: Int,
    val executedLots: Int,
    val type: OrderType,
    val price: Double,

    var stock: Stock?
) : BaseOrder() {

    fun getOperationStatusString(): String {
        if (operation == OperationType.BUY) {
            return "Покупка"
        } else if (operation == OperationType.SELL) {
            return "Продажа"
        }

        return ""
    }

    override fun getLotsExecuted(): Int = executedLots
    override fun getLotsRequested(): Int = requestedLots
    override fun getBrokerColor(): Int { return Utils.TINKOFF }
}