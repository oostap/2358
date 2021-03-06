package com.project.ti2358.data.model.streamAlor

data class CancelEventBody(
    val token: String,          // "token": "@#FPI@#HGP@IJFPF#@",
    val opcode: String,         // "opcode": "unsubscribe",
    val guid: String,           // "guid": "f35a2373-612c-4518-54af-72025384f59b"
)