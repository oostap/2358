package com.project.ti2358.data.manager

import com.project.ti2358.data.tinkoff.model.Orderbook
import com.project.ti2358.data.tinkoff.model.PortfolioPosition
import com.project.ti2358.data.tinkoff.service.MarketService
import com.project.ti2358.data.tinkoff.service.OrdersService
import com.project.ti2358.ui.orderbook.OrderbookLine
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@KoinApiExtension
class PositionManager() : KoinComponent {
    private val marketService: MarketService by inject()
    private val stockManager: StockManager by inject()
    private val portfolioManager: PortfolioManager by inject()
    private val ordersService: OrdersService by inject()

    var activePosition: PortfolioPosition? = null
    var orderbook: MutableList<OrderbookLine> = mutableListOf()

    fun start(position: PortfolioPosition) {
        activePosition = position
    }

    suspend fun loadOrderbook(): Orderbook? {
        activePosition?.let {
            return marketService.orderbook(it.figi, 10)
        }
        return null
    }
}