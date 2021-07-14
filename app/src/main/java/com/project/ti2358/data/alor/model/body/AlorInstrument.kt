package com.project.ti2358.data.alor.model.body

import com.project.ti2358.data.alor.model.AlorExchange

//"instrument": {
//    "symbol": "SBER",
//    "exchange": "MOEX"
//}

data class AlorInstrument(
    val symbol: String,
    val exchange: AlorExchange,
)