package com.project.ti2358.data.manager

enum class LimitType {
    ON_UP,
    ON_DOWN,

    NEAR_UP,
    NEAR_DOWN,

    ABOVE_UP,
    UNDER_DOWN
}

data class LimitStock(
    val stock: Stock,
    val type: LimitType,

    val percentFire: Double,
    val priceFire: Double,

    val fireTime: Long,
) {
    var ticker = stock.ticker
    var figi = stock.figi
}