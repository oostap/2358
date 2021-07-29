package com.project.ti2358.data.tinkoff.model

import com.project.ti2358.data.common.BaseOperation
import com.project.ti2358.data.manager.Stock
import java.util.*

data class TinkoffOperation (
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

) : BaseOperation() {
    override fun getOperationID(): String = id
    override fun getOperationDate(): Date = date
    override fun getOperationDone(): Boolean = status == OperationStatus.DONE
    override fun getLotsExecuted(): Int = quantityExecuted
    override fun getType(): OperationType = operationType
    override fun getOperationPrice(): Double = price
}