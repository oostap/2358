package com.project.ti2358.data.common

import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.tinkoff.model.OperationType
import java.util.*

open class BaseOperation() {
    open fun getOperationID(): String = ""
    open fun getOperationDate(): Date = Calendar.getInstance().time

    open fun getOperationDone(): Boolean = false
    open fun getLotsExecuted(): Int = 0
    open fun getType(): OperationType = OperationType.NONE

    open fun getOperationPrice(): Double = 0.0

    var stock: Stock? = null
}