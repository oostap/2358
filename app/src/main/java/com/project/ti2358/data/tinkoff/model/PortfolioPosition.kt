package com.project.ti2358.data.tinkoff.model

import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.tinkoff.model.InstrumentType
import com.project.ti2358.data.tinkoff.model.MoneyAmount

data class PortfolioPosition(
    val figi: String,
    val ticker: String,
    val isin: String,
    val name: String,

    val lots: Int,

    val instrumentType: InstrumentType,
    val balance: Double,
    val blocked: Double,
    val expectedYield: MoneyAmount?,
    val averagePositionPrice: MoneyAmount?,
    val averagePositionPriceNoNkd: MoneyAmount?,

    var stock: Stock?
) {
    fun getProfitAmount(): Double {
        return expectedYield?.value ?: 0.0
    }

    fun getAveragePrice(): Double {
        return averagePositionPrice?.value ?: 0.0
    }

    fun getProfitPercent(): Double {
        val profit = getProfitAmount()
        val totalCash = balance * getAveragePrice()
        return if (totalCash == 0.0) 0.0 else (100 * profit) / totalCash
    }
}