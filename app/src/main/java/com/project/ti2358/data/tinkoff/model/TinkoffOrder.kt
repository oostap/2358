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

    val rejectReason: String,
    val message: String,
    val commission: MoneyAmount?,

) : BaseOrder() {

    override fun getOperationStatusString(): String {
        if (operation == OperationType.BUY) {
            return "Покупка"
        } else if (operation == OperationType.SELL) {
            return "Продажа"
        }

        return ""
    }

    override fun getLotsExecuted(): Int = executedLots
    override fun getLotsRequested(): Int = requestedLots
    override fun getBrokerColor(bright: Boolean): Int {
        return if (bright) Utils.TINKOFF_BRIGHT else Utils.TINKOFF
    }

    override fun isCreated(): Boolean = (status == OrderStatus.NEW || status == OrderStatus.PENDING_NEW)

    override fun getOrderID(): String = orderId

    override fun getOrderStock(): Stock? { return stock }
    override fun getOrderPrice(): Double { return price }

    override fun getOrderOperation(): OperationType = operation
}