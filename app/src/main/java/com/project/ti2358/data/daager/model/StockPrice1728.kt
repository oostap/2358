package com.project.ti2358.data.daager.model

import com.google.gson.annotations.SerializedName

data class StockPrice1728 (
    @SerializedName("t1200")
    val from700to1200: StockStep1728?,

    @SerializedName("t1600")
    val from700to1600: StockStep1728?,

    @SerializedName("t1635")
    val from1630to1635: StockStep1728?
)