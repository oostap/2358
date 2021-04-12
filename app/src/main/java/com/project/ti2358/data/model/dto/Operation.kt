package com.project.ti2358.data.model.dto

import android.annotation.SuppressLint
import com.project.ti2358.data.manager.Stock
import java.text.SimpleDateFormat
import java.util.*

data class Operation (
    val id: String,
    val status: OperationStatus,
    val trades: List<OperationTrade>,
    val commission: MoneyAmount?,
    val currency: Currency,
    val payment: Double,
    val price: Double,
    val quantity: Int,
    val quantityExecuted: Int,
    val figi: String,
    val instrumentType: InstrumentType,
    val isMarginCall: Boolean,
    val date: Date,
    val operationType: OperationType,

    var stock: Stock?
)