package com.project.ti2358.data.manager

data class StockTrend(
    val stock: Stock,
    val priceStart: Double,
    val priceLow: Double,
    val priceNow: Double,

    val changeFromStartToLow: Double,
    val changeFromLowToNow: Double,
    val turnValue: Double,

    val timeFromStartToLow: Int,
    val timeFromLowToNow: Int,

    val fireTime: Long,
) {
    var ticker = stock.ticker
    var figi = stock.figi
}