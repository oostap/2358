package com.project.ti2358.data.model.dto

import com.project.ti2358.data.service.Stock
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
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
    val averagePositionPrice: MoneyAmount?,
    val averagePositionPriceNoNkd: MoneyAmount,

    var stock: Stock?
) {
    fun getProfitAmount(): String {
        return "${expectedYield.value} $"
    }

    fun getAveragePrice(): Double {
        return averagePositionPrice?.value ?: 0.0
    }
}