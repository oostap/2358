package com.project.ti2358.data.manager

import com.project.ti2358.service.LimitType

data class StockLimit(
    val stock: Stock,
    val type: LimitType,

    val percentFire: Double,
    val priceFire: Double,

    val fireTime: Long,
) {
    var ticker = stock.ticker
    var figi = stock.figi
}