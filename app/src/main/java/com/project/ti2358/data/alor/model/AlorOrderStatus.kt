package com.project.ti2358.data.alor.model

import com.google.gson.annotations.SerializedName

enum class AlorOrderStatus {
    @SerializedName("working") Working,
    @SerializedName("filled") Filled,
    @SerializedName("canceled") Canceled,
    @SerializedName("rejected") Rejected,
}