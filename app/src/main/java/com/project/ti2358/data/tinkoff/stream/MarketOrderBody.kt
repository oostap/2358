package com.project.ti2358.data.tinkoff.stream

import com.project.ti2358.data.tinkoff.model.OperationType

data class MarketOrderBody(val lots: Int, val operation: OperationType)