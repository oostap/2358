package com.project.ti2358.data.tinkoff.model

import com.google.gson.annotations.SerializedName
import com.project.ti2358.data.common.BasePosition
import com.project.ti2358.data.manager.Stock

data class TinkoffPosition(
    val figi: String,
    val ticker: String,
    val isin: String,
    val name: String,

    @SerializedName("lots")
    val count: Int,

    val instrumentType: InstrumentType,
    val balance: Double,
    val blocked: Double,
    val expectedYield: MoneyAmount?,
    val averagePositionPrice: MoneyAmount?,
    val averagePositionPriceNoNkd: MoneyAmount?,

    var stock: Stock?
): BasePosition() {
    override fun getAveragePrice(): Double {
        return averagePositionPrice?.value ?: 0.0
    }

    override fun getLots(): Int = count

    override fun getProfitAmount(): Double {
        return expectedYield?.value ?: 0.0
    }

    override fun getProfitPercent(): Double {
        val profit = getProfitAmount()
        val totalCash = balance * getAveragePrice()
        return if (totalCash == 0.0) 0.0 else (100 * profit) / totalCash
    }

    override fun getPositionStock(): Stock? = stock
}