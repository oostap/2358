package com.project.ti2358.data.alor.model

import com.google.gson.annotations.SerializedName

enum class AlorOrderType {
    @SerializedName("limit") Limit,
    @SerializedName("market") Market,
    @SerializedName("stop") Stop,
}