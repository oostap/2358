package com.project.ti2358.data.tinkoff.stream

import com.project.ti2358.data.tinkoff.model.Interval

data class CandleEventBody(val event: String, val figi: String, val interval: Interval)