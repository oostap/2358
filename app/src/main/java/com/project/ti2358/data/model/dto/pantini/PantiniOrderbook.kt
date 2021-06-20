package com.project.ti2358.data.model.dto.pantini

import com.project.ti2358.data.model.dto.BidAsk
import com.project.ti2358.service.log
import org.json.JSONObject

// пул: bid|bidSize|ask|askSize
data class PantiniOrderbook (
    val ticker: String,
) {
    var orderbook: MutableMap<String, Pair<BidAsk, BidAsk>> = mutableMapOf()
    fun create(json: JSONObject) {
        for (key in json.keys()) {
            val value = json.getString(key)
            val bidask = value.split("|")
            if (bidask.size == 4) {
                val bid = BidAsk(bidask[0].toDouble(), bidask[1].toInt())
                val ask = BidAsk(bidask[2].toDouble(), bidask[3].toInt())
                orderbook[key] = Pair(bid, ask)
            } else {
                log("PantiniOrderbook ERROR! $value")
            }
        }
    }
}