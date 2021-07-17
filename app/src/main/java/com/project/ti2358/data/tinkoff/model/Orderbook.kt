package com.project.ti2358.data.tinkoff.model

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
        if (asks.isEmpty()) return 0.0

        if (quantity == 0) return asks.first().price

        var total = 0
        for (ask in asks) {
            total += ask.quantity
            if (total >= quantity * 2) { // берём с запасом, чтобы не проскочить аск
                return ask.price
            }
        }
        return asks.last().price
    }

    fun getBestPriceFromBid(quantity: Int): Double {
        if (bids.isEmpty()) return 0.0

        if (quantity == 0) return bids.first().price

        var total = 0
        for (ask in bids) {
            total += ask.quantity
            if (total >= quantity) {
                return ask.price
            }
        }
        return bids.last().price
    }
}