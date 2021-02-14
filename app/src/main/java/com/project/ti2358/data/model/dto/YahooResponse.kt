package com.project.ti2358.data.model.dto

import com.google.gson.annotations.SerializedName
import java.util.*

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
        if (postMarketPrice.raw != 0.0) return postMarketPrice.raw

        // иначе обычную
        return regularMarketPrice.raw
    }
}
