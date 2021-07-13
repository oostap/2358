package com.project.ti2358.data.alor.model

//    "tradeServersInfo": [
//    {
//        "tradeServerCode": "FX1",
//        "addresses": "",
//        "type": "",
//        "contracts": "",
//        "market": "",
//        "accountNum": ""
//    }
data class AlorTradeServerInfo(
    val tradeServerCode: String,
    val addresses: String,
    val type: String,
    val contracts: String,
    val market: String,
    val accountNum: String,
)