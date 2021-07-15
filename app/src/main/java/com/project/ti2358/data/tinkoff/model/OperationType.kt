package com.project.ti2358.data.tinkoff.model

import com.google.gson.annotations.SerializedName

enum class OperationType {
    @SerializedName("Buy", alternate = ["buy"]) BUY,
    @SerializedName("Sell", alternate = ["sell"]) SELL
}

//enum class OperationSide {
//    @SerializedName("uy") BUY,
//    @SerializedName("ell") SELL
//}