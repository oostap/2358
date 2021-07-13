package com.project.ti2358.data.alor.model

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
    val side: AlorOrderSide,
    val status: AlorOrderStatus,
    val transTime: Date,
    val endTime: Date,
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
)