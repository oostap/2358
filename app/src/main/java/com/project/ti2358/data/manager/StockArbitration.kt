package com.project.ti2358.data.manager

data class StockArbitration(
    val stock: Stock,
    val askRU: Double,
    val bidRU: Double,
    val priceUS: Double,

    val lots: Int,

    val long: Boolean,
    val fireTime: Long
) {
    var ticker = stock.ticker
    var figi = stock.figi

    var changePriceAbsolute: Double = 0.0
    var changePricePercent: Double = 0.0
}