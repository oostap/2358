package com.project.ti2358.data.model.dto

import com.google.gson.annotations.SerializedName

enum class Interval {
    @SerializedName("1min")
    MINUTE,
    @SerializedName("2min")
    TWO_MINUTES,
    @SerializedName("3min")
    THREE_MINUTES,
    @SerializedName("5min")
    FIVE_MINUTES,
    @SerializedName("10min")
    TEN_MINUTES,
    @SerializedName("15min")
    FIFTEEN_MINUTES,
    @SerializedName("30min")
    THIRTY_MINUTES,
    @SerializedName("hour")
    HOUR,
    @SerializedName("2hour")
    TWO_HOURS,
    @SerializedName("4hour")
    FOUR_HOURS,
    @SerializedName("day")
    DAY,
    @SerializedName("week")
    WEEK,
    @SerializedName("month")
    MONTH,
}