package com.project.ti2358.data.pantini.model

import java.util.*

//time + '|' + price + '|' + condition + '|' + exchange + '|' + size + '|' + hit
//1624038539527|27.38|@ T |QD|350|1","1624038540152|27.39|@ TI|PA|33|3
data class PantiniPrint (
    val time: Calendar,
    val price: Double,
    val condition: String,
    val exchange: String,
    val size: Int,
    val hit: Int
)