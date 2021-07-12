package com.project.ti2358.data.tinkoff.model

import java.util.*

data class OperationTrade (
    private val tradeId: String,
    private val date: Date,
    private val price: Double,
    private val quantity: Int
)