package com.project.ti2358.data.model.dto

import com.google.gson.annotations.SerializedName
import java.util.*

data class Stocks (
    val total : Int,
    val instruments: List<MarketInstrument>
)
