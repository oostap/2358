package com.project.ti2358.data.daager.model

import android.graphics.Color

data class Sector (
    val rus: String,
    val eng: String,
) {
    fun getColor(): Int {
        return when (eng) {
            "HealthCare" -> Color.parseColor("#d9bc3f")
            "Consumer" -> Color.parseColor("#9d5451")
            "IT" -> Color.parseColor("#19cabf")
            "Industrials" -> Color.parseColor("#af824f")
            "Telecom" -> Color.parseColor("#d1916a")
            "Materials" -> Color.parseColor("#a469af")
            "Financial" -> Color.parseColor("#3ca4f5")
            "Energy" -> Color.parseColor("#bc8fc0")
            "Utilities" -> Color.parseColor("#52a35e")
            else -> Color.parseColor("#E74C3C")
        }
    }
}