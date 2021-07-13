package com.project.ti2358.data.alor.model

//{
//    "portfolio": "G14975",
//    "tks": "MB0014100002",
//    "tradeServersInfo": [
//    {
//        "tradeServerCode": "FX1",
//        "addresses": "",
//        "type": "",
//        "contracts": "",
//        "market": "",
//        "accountNum": ""
//    }
//}

data class AlorTradeServer(
    val portfolio: String,  // G14975
    val tks: String,        // MB0014100002
    var tradeServersInfo: List<AlorTradeServerInfo>
) {
//    fun getTradeServerCode(): String {
//        if (tradeServersInfo.isNotEmpty()) {
//            return tradeServersInfo.first().tradeServerCode
//        }
//        return ""
//    }
}