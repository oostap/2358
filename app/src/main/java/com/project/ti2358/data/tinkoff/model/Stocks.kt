package com.project.ti2358.data.tinkoff.model

import com.project.ti2358.data.tinkoff.model.Instrument

data class Stocks (
    val total : Int,
    val instruments: List<Instrument>
)
