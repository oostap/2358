package com.project.ti2358.data.alor.model

//{
//    "buyingPowerAtMorning": 439844.15,
//    "buyingPower": 452404,
//    "profit": 12560,
//    "profitRate": 1.93,
//    "portfolioEvaluation": 651717,
//    "portfolioLiquidationValue": 651717,
//    "initialMargin": 199313,
//    "riskBeforeForcePositionClosing": 552061
//}

data class AlorSummary(
    val buyingPowerAtMorning: Double,
    val buyingPower: Double,
    val profit: Double,
    val profitRate: Double,
    val portfolioEvaluation: Double,
    val portfolioLiquidationValue: Double,
    val initialMargin: Double,
    val riskBeforeForcePositionClosing: Double
)