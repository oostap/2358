package com.project.ti2358.data.alor.model

import com.google.gson.annotations.SerializedName

enum class AlorOrderStatus {
    @SerializedName("working") WORKING,
    @SerializedName("filled") FILLED,
    @SerializedName("canceled") CANCELED,
    @SerializedName("rejected") REJECTED,
}