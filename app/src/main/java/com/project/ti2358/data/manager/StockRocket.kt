package com.project.ti2358.data.manager

data class StockRocket(
    val stock: Stock,
    val priceFrom: Double,
    val priceTo: Double,
    val time: Int,
    val volume: Int,
    val changePercent: Double,
    val fireTime: Long
) {
    var ticker = stock.ticker
    var figi = stock.figi

    var changePriceRocketAbsolute: Double = 0.0
    var changePriceRocketPercent: Double = 0.0

    fun process() {
        changePriceRocketAbsolute = priceTo - priceFrom
        changePriceRocketPercent = priceTo / priceFrom * 100.0 - 100.0
    }
}