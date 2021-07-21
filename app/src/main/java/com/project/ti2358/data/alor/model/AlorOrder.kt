package com.project.ti2358.data.alor.model

import com.project.ti2358.data.common.BaseOrder
import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.tinkoff.model.OperationType
import com.project.ti2358.data.tinkoff.model.OrderStatus
import com.project.ti2358.service.Utils
import java.util.*

//{
//    "id": "18995978560",
//    "symbol": "SBER",
//    "brokerSymbol": "MOEX:SBER",
//    "exchange": "MOEX",
//    "type": "market",
//    "side": "buy",
//    "status": "filled",
//    "transTime": "2020-06-16T23:59:59.9990000",
//    "endTime": "2020-06-16T23:59:59.9990000",
//    "qtyUnits": 1,
//    "qtyBatch": 1,
//    "qty": 1,
//    "filledQtyUnits": 1,
//    "filledQtyBatch": 1,
//    "filled": 1,
//    "price": 208.6,
//    "existing": true
//}

data class AlorOrder(
    val id: String,
    val symbol: String,
    val brokerSymbol: String,
    val exchange: AlorExchange,
    val type: AlorOrderType,
    val side: OperationType,
    val status: AlorOrderStatus,
    val transTime: Date,
    val qtyUnits: Int,
    val qtyBatch: Int,
    val qty: Int,
    val filledQtyUnits: Int,
    val filledQtyBatch: Int,
    val filled: Int,
    val price: Double,
    val existing: Boolean,

    // для стоп ордеров
    val stopPrice: Double?,
    val endTime: String,

) : BaseOrder() {
    override fun getOperationStatusString(): String {
        if (side == OperationType.BUY) {
            return "Покупка"
        } else if (side == OperationType.SELL) {
            return "Продажа"
        }

        return ""
    }

    override fun getLotsExecuted(): Int = filled
    override fun getLotsRequested(): Int = qtyUnits
    override fun getBrokerColor(bright: Boolean): Int {
        return if (bright) Utils.ALOR_BRIGHT else Utils.ALOR
    }

    override fun isCreated(): Boolean = (status == AlorOrderStatus.WORKING)

    override fun getOrderID(): String = id

    override fun getOrderStock(): Stock? { return stock }
    override fun getOrderPrice(): Double { return price }

    override fun getOrderOperation(): OperationType = side
}