package com.project.ti2358.data.tinkoff.stream

data class OrderEventBody (val event: String, val figi: String, val depth: Int)