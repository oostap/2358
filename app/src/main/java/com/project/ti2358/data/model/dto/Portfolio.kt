package com.project.ti2358.data.model.dto

import org.koin.core.component.KoinApiExtension

@KoinApiExtension
data class Portfolio (
    val positions: List<PortfolioPosition>
)