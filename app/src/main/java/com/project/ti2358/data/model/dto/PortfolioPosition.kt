package com.project.ti2358.data.model.dto

import com.project.ti2358.data.service.Stock

data class PortfolioPosition(
    val figi: String,
    val ticker: String,
    val isin: String,
    val name: String,

    val lots: Int,

    val instrumentType: InstrumentType,
    val balance: Double,
    val blocked: Double,
    val expectedYield: MoneyAmount,
    val averagePositionPrice: MoneyAmount,
    val averagePositionPriceNoNkd: MoneyAmount,

    var stock: Stock?
) {

    fun getProfitAmount(): String {
        return "${expectedYield.value} $"
    }
}