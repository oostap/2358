package com.project.ti2358.data.model.streamAlor

data class BarGetEventBody(
    val token: String,          // "token": "@#FPI@#HGP@IJFPF#@",
    val opcode: String,         // "opcode": "BarsGetAndSubscribe",
    val code: String,           // "code": "AAPL",

    val exchange: String,       // "exchange": "SPBEX",
    val tf: Any,                // 60
    val from: Long,             // 1536057084
    val format: String,         // "format": "Simple",
    val delayed: Boolean,       // "delayed": false,
    val guid: String,           // "guid": "f35a2373-612c-4518-54af-72025384f59b"
)