package com.project.ti2358.data.model.dto.pantini

import com.project.ti2358.data.model.dto.BidAsk
import com.project.ti2358.service.log
import org.json.JSONObject
import java.lang.Exception

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
                val bidPrice = try {
                    bidask[0].toDouble()
                } catch (e: Exception) {
                    0.0
                }

                val bidCount = try {
                    bidask[1].toInt()
                } catch (e: Exception) {
                    0
                }

                val askPrice = try {
                    bidask[2].toDouble()
                } catch (e: Exception) {
                    0.0
                }

                val askCount = try {
                    bidask[3].toInt()
                } catch (e: Exception) {
                    0
                }

                val bid = BidAsk(bidPrice, bidCount)
                val ask = BidAsk(askPrice, askCount)

                orderbook[key] = Pair(bid, ask)
            } else {
                log("PantiniOrderbook ERROR! $value")
            }
        }
    }
}