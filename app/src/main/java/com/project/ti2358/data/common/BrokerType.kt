package com.project.ti2358.data.common

import com.google.gson.annotations.SerializedName

enum class BrokerType {
    @SerializedName("none") NONE,
    @SerializedName("tinkoff") TINKOFF,
    @SerializedName("alor") ALOR,
}