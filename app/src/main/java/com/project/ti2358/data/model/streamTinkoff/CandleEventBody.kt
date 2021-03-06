package com.project.ti2358.data.model.streamTinkoff

import com.project.ti2358.data.model.dto.Interval

data class CandleEventBody(val event: String, val figi: String, val interval: Interval)