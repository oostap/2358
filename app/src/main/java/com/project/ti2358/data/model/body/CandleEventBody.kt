package com.project.ti2358.data.model.body

import com.project.ti2358.data.model.dto.Interval

class CandleEventBody (
    val event: String,
    val figi: String,
    val interval: Interval
)