package com.project.ti2358.data.model.streamTinkoff

import com.project.ti2358.data.model.dto.OperationType

data class LimitOrderBody(val lots: Int, val price: Double, val operation: OperationType)