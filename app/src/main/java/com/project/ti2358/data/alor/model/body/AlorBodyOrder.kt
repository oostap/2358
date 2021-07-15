package com.project.ti2358.data.alor.model.body

import com.project.ti2358.data.alor.model.AlorOrderType
import com.project.ti2358.data.tinkoff.model.OperationType

//{
//    "side": "buy",
//    "type": "limit",
//    "quantity": 2,
//    "price": 190.97,
//    "instrument": {
//          "symbol": "SBER",
//          "exchange": "MOEX"
//      },
//    "user": {
//          "account": "L01-00000F00",
//          "portfolio": "D39004"
//      }
//}

data class AlorBodyOrder(
    val side: OperationType,
    val quantity: Int,
    val instrument: AlorInstrument,
    val user: AlorUser,

    val type: AlorOrderType? = null,
    val price: Double? = null,

    val triggerPrice: Double? = null,

    val orderEndUnixTime: Long? = null
)