package com.project.ti2358.data.alor.model

import com.project.ti2358.data.common.BasePosition
import com.project.ti2358.data.manager.Stock

//{
//    "symbol": "LKOH",
//    "brokerSymbol": "MOEX:LKOH",
//    "exchange": "MOEX",
//    "avgPrice": 16.6,
//    "qtyUnits": 20,
//    "openUnits": 30,
//    "lotSize": 1,
//    "shortName": "ЛУКОЙЛ",
//    "qtyT0": 20,
//    "qtyT1": 20,
//    "qtyT2": 20,
//    "qtyTFuture": 20,
//    "qtyT0Batch": 20,
//    "qtyT1Batch": 20,
//    "qtyT2Batch": 20,
//    "qtyTFutureBatch": 20,
//    "qtyBatch": 20,
//    "openQtyBatch": 20,
//    "qty": 20,
//    "open": 20,
//    "unrealisedPl": 3250,
//    "isCurrency": false
//}

data class AlorPosition(
    val symbol: String,
    val brokerSymbol: String,
    val exchange: AlorExchange,
    val avgPrice: Double,
    val qtyUnits: Double,
    val openUnits: Double,
    val lotSize: Int,
    val shortName: String,
    val qtyT0: Double,
    val qtyT1: Double,
    val qtyT2: Double,
    val qtyTFuture: Double,
    val qtyT0Batch: Double,
    val qtyT1Batch: Double,
    val qtyT2Batch: Double,
    val qtyTFutureBatch: Double,
    val qtyBatch: Double,
    val openQtyBatch: Double,
    val qty: Double,
    val open: Double,
    val unrealisedPl: Double,
    val isCurrency: Boolean,

    var stock: Stock?
) : BasePosition() {
    override fun getAveragePrice(): Double = avgPrice

    override fun getLots(): Int {
        return qtyUnits.toInt()
    }

    override fun getBlocked(): Int {
        return openUnits.toInt()
    }

    override fun getProfitAmount(): Double {
        if (stock != null) {
            return getLots() * (stock!!.getPriceRaw() - avgPrice)
        }
        return 0.0
    }

    override fun getProfitPercent(): Double {
        val profit = getProfitAmount()
        val totalCash = getLots() * avgPrice
        return if (totalCash == 0.0) 0.0 else (100 * profit) / totalCash
    }

    override fun getPositionStock(): Stock? = stock
}
