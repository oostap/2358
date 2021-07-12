package com.project.ti2358.data.daager.model

data class PresetStock (
    val ticker: String,
    var percent: Double = -1.0,
    var lots: Int = 0,
    var profit: Double = 1.0
)