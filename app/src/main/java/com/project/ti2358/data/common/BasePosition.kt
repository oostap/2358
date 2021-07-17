package com.project.ti2358.data.common

import com.project.ti2358.data.manager.Stock

open class BasePosition() {
    open fun getAveragePrice(): Double = 0.0

    open fun getLots(): Int = 0
    open fun getBlocked(): Int = 0
    open fun getProfitAmount(): Double = 0.0
    open fun getProfitPercent(): Double = 0.0

    open fun getPositionStock(): Stock? = null
}