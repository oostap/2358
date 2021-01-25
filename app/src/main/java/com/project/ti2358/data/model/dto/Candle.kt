package com.project.ti2358.data.model.dto

import com.google.gson.annotations.SerializedName
import java.util.*

data class Candle (
    @SerializedName("o")
    val openingPrice: Double,
    @SerializedName("c")
    val closingPrice: Double,
    @SerializedName("h")
    val highestPrice: Double,
    @SerializedName("l")
    val lowestPrice: Double,
    @SerializedName("v")
    val volume: Int,

    val time: Date,
    val interval: Interval,
    val figi: String
) {
    constructor() : this(0.0,0.0,0.0,0.0,0,Calendar.getInstance().time,Interval.DAY, "")
}
