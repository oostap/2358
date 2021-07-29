package com.project.ti2358.data.alor.model

import com.project.ti2358.data.common.BaseOperation
import com.project.ti2358.data.tinkoff.model.*
import com.project.ti2358.data.tinkoff.model.Currency
import java.util.*

//{
//    "id": 159,
//    "orderno": 7271479,
//    "symbol": "GAZP",
//    "brokerSymbol": "GAZP:MOEX",
//    "exchange": "MOEX",
//    "date": "2018-08-07T08:40:03.445Z",
//    "board": "TQBR",
//    "qtyUnits": 1,
//    "qtyBatch": 1,
//    "qty": 1,
//    "price": 142.52,
//    "side": "buy",
//    "existing": false
//}

data class AlorOperation (
    val id: String,
    val orderno: String,
    val symbol: String,
    val brokerSymbol: String,
    val exchange: AlorExchange,
    val date: Date,
    val board: String,
    val qtyUnits: Double,
    val qtyBatch: Double,
    val qty: Double,
    val price: Double,
    val side: OperationType,
    val existing: Boolean
): BaseOperation() {
    override fun getOperationID(): String = id
    override fun getOperationDate(): Date = date
    override fun getOperationDone(): Boolean = true
    override fun getLotsExecuted(): Int = qty.toInt()
    override fun getType(): OperationType = side
    override fun getOperationPrice(): Double = price
}