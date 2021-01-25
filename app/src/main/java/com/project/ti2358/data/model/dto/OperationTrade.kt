package com.project.ti2358.data.model.dto

import java.util.*

data class OperationTrade (
    private val tradeId: String,
    private val date: Date,
    private val price: Double,
    private val quantity: Int
)