package com.project.ti2358.data.common

import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.tinkoff.model.OperationType
import retrofit2.Retrofit

open class BaseOrder() {
    open fun getOperationStatusString(): String = ""

    open fun getLotsExecuted(): Int = 0
    open fun getLotsRequested(): Int = 0
    open fun getBrokerColor(bright: Boolean = false): Int = 0

    open fun isCreated(): Boolean = false

    open fun getOrderID(): String = ""

    open fun getOrderPrice(): Double = 0.0

    open fun getOrderOperation(): OperationType = OperationType.NONE

    var stock: Stock? = null
}