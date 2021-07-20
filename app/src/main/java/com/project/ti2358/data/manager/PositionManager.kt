package com.project.ti2358.data.manager

import com.project.ti2358.data.tinkoff.model.Orderbook
import com.project.ti2358.data.tinkoff.model.TinkoffPosition
import com.project.ti2358.data.tinkoff.service.MarketService
import com.project.ti2358.ui.orderbook.OrderbookLine
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@KoinApiExtension
class PositionManager() : KoinComponent {
    private val marketService: MarketService by inject()

    var activePositionTinkoff: TinkoffPosition? = null
    var orderbook: MutableList<OrderbookLine> = mutableListOf()

    fun start(positionTinkoff: TinkoffPosition) {
        activePositionTinkoff = positionTinkoff
    }

    suspend fun loadOrderbook(): Orderbook? {
        activePositionTinkoff?.let {
            return marketService.orderbook(it.figi, 10)
        }
        return null
    }
}