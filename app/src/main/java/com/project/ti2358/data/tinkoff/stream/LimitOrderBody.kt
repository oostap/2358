package com.project.ti2358.data.tinkoff.stream

import com.project.ti2358.data.tinkoff.model.OperationType

data class LimitOrderBody(val lots: Int, val price: Double, val operation: OperationType)