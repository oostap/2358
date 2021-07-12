package com.project.ti2358.data.tinkoff.model

import com.google.gson.annotations.SerializedName

enum class InstrumentType {
    @SerializedName("Stock") STOCK,
    @SerializedName("Currency") CURRENCY,
    @SerializedName("Bond") BOND,
    @SerializedName("Etf") ETF
}