package com.project.ti2358.data.model.dto.daager

import com.google.gson.annotations.SerializedName

data class StockStep1728 (
    @SerializedName("o")
    val openPrice: Double?,

    @SerializedName("c")
    val closePrice: Double,

    @SerializedName("v")
    val volume: Int
)