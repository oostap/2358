package com.project.ti2358.data.model.dto

import com.google.gson.annotations.SerializedName

enum class OperationType {
    @SerializedName("Buy") BUY,
    @SerializedName("Sell") SELL
}