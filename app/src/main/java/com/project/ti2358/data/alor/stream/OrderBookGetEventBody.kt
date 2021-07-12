package com.project.ti2358.data.alor.stream

data class OrderBookGetEventBody(
    val token: String,          // "token": "@#FPI@#HGP@IJFPF#@",
    val opcode: String,         // "opcode": "OrderBookGetAndSubscribe",
    val code: String,           // "code": "AAPL",

    val exchange: String,       // "exchange": "SPBEX",
    val depth: Int,             // 20
    val format: String,         // "format": "Simple",
    val delayed: Boolean,       // "delayed": false,
    val guid: String,           // "guid": "f35a2373-612c-4518-54af-72025384f59b"
)