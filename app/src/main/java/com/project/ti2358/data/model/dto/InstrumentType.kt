package com.project.ti2358.data.model.dto

import com.google.gson.annotations.SerializedName

enum class InstrumentType {
    @SerializedName("Stock") STOCK,
    @SerializedName("Currency") CURRENCY,
    @SerializedName("Bond") BOND,
    @SerializedName("Etf") ETF
}