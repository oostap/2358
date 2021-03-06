package com.project.ti2358.data.model.streamTinkoff

import com.project.ti2358.data.model.dto.OperationType

data class MarketOrderBody(val lots: Int, val operation: OperationType)