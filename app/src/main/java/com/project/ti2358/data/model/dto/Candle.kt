package com.project.ti2358.data.model.dto

import com.google.gson.annotations.SerializedName
import com.icechao.klinelib.model.KLineEntity
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

) : KLineEntity() {

    override fun getDate(): Long {
        return time.time
    }

    override fun getOpenPrice(): Float {
        return openingPrice.toFloat()
    }

    override fun getHighPrice(): Float {
        return highestPrice.toFloat()
    }

    override fun getLowPrice(): Float {
        return lowestPrice.toFloat()
    }

    override fun getClosePrice(): Float {
        return closingPrice.toFloat()
    }

    override fun getVolume(): Float {
        return volume.toFloat()
    }

    constructor() : this(0.0,0.0,0.0,0.0,0,Calendar.getInstance().time,Interval.DAY, "")
}
