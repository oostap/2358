package com.project.ti2358.data.model.dto

import com.google.gson.annotations.SerializedName

enum class OrderType {
    @SerializedName("Market") MARKET,
    @SerializedName("Limit") LIMIT,
}