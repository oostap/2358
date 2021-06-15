package com.project.ti2358.data.manager

data class LimitStock(
    val stock: Stock,
    val up: Boolean,
    val priceNow: Double,
    val fireTime: Long,
) {
    var ticker = stock.ticker
    var figi = stock.figi
}