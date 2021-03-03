package com.project.ti2358.data.model.dto.yahoo

data class YahooResponse (
    val preMarketPrice: YahooPrice,
    val postMarketPrice: YahooPrice,
    val regularMarketPrice: YahooPrice,
    val regularMarketDayHigh: YahooPrice,
    val regularMarketDayLow: YahooPrice,
    val regularMarketVolume: YahooPrice,
    val regularMarketOpen: YahooPrice,
    val postMarketTime: Long,

    val exchange: String?,       // "NMS"
    val exchangeName: String?,   // "NasdaqGS"
) {
    fun getLastPrice(): Double {
        // если у бумаги есть постмаркет, то берём её
        if (postMarketPrice.raw != 0.0) return postMarketPrice.raw ?: 0.0

        // иначе обычную
        return regularMarketPrice.raw ?: 0.0
    }

    fun getVolumeShares(): Int {
        return regularMarketVolume.raw?.toInt() ?: 0
    }

    fun getVolumeCash(): Int {
        val high = regularMarketDayHigh.raw ?: 0.0
        val low = regularMarketDayLow.raw ?: 0.0
        val volume = regularMarketVolume.raw ?: 0.0
        val price = (low + high) / 2.0
        return (volume * price).toInt()
    }
}
