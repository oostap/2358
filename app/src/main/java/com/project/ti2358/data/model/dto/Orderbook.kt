package com.project.ti2358.data.model.dto

data class Orderbook(
    val figi: String,
    val depth: Int,
    val bids: List<BidAsk>,
    val asks: List<BidAsk>,

    val tradeStatus: String,
    val minPriceIncrement: Double,
    val faceValue: Double,
    val lastPrice: Double,
    val closePrice: Double,
    val limitUp: Double,
    val limitDown: Double,
) {
    fun getBestPriceFromAsk(quantity: Int): Double {
        var total = 0
        for (ask in asks) {
            total += ask.quantity
            if (total >= quantity) {
                return ask.price
            }
        }
        return 0.0
    }
}