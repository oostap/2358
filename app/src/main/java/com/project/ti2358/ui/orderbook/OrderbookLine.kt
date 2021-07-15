package com.project.ti2358.ui.orderbook

import com.project.ti2358.data.common.BaseOrder
import com.project.ti2358.data.manager.Stock

class OrderbookLine(
    var stock: Stock
) {
    var bidCount: Int = 0
    var bidPrice: Double = 0.0
    var bidPercent: Double = 0.0

    var askCount: Int = 0
    var askPrice: Double = 0.0
    var askPercent: Double = 0.0

    var ordersBuy: MutableList<BaseOrder> = mutableListOf()
    var ordersSell: MutableList<BaseOrder> = mutableListOf()

    var exchange: String = ""
}