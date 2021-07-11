package com.project.ti2358.data.model.dto.daager

data class PresetStock (
    val ticker: String,
    var percent: Double = -1.0,
    var lots: Int = 0,
    var profit: Double = 1.0
)