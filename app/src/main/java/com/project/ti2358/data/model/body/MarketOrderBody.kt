package com.project.ti2358.data.model.body

import com.project.ti2358.data.model.dto.OperationType

data class MarketOrderBody(val lots: Int, val operation: OperationType)