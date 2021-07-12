package com.project.ti2358.data.tinkoff.model

import com.google.gson.annotations.SerializedName

enum class OperationType {
    @SerializedName("Buy") BUY,
    @SerializedName("Sell") SELL
}