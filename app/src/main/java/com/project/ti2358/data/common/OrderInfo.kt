package com.project.ti2358.data.common

import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.tinkoff.model.OperationType

data class OrderInfo(
    val id: String,
    val success: Boolean,
    val stock: Stock,
    val brokerType: BrokerType,
    val operationType: OperationType
)