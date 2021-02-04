package com.project.ti2358.data.service

import com.project.ti2358.data.manager.PurchasePosition
import com.project.ti2358.data.manager.PurchaseStatus
import com.project.ti2358.data.model.dto.PortfolioPosition
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@KoinApiExtension
class Strategy1000() : KoinComponent {
    private val stockManager: StockManager by inject()
    private val depositManager: DepositManager by inject()
    private val ordersService: OrdersService by inject()

    var positions: MutableList<PortfolioPosition> = mutableListOf()
    var positionsSelected: MutableList<PortfolioPosition> = mutableListOf()
    var positionsToSell: MutableList<PurchasePosition> = mutableListOf()

    public fun process() : MutableList<PortfolioPosition> {
        val all = depositManager.portfolioPositions

        positions.clear()
        positions.addAll(all)

        positions.sortByDescending { it.lots * it.getAveragePrice() }
        return positions
    }

    public fun setSelected(position: PortfolioPosition, value : Boolean) {
        if (value) {
            positionsSelected.removeAll { it.figi == position.figi }
        } else {
            var filter = positionsSelected.filter { it.figi == position.figi }
            if (filter.isEmpty())
                positionsSelected.add(position)
        }
        positionsSelected.sortByDescending { it.lots * it.getAveragePrice() }
    }

    public fun isSelected(position: PortfolioPosition) : Boolean {
        var filter = positionsSelected.filter { it.figi == position.figi }
        return filter.isNotEmpty()
    }

    public fun getSellPosition() : MutableList<PurchasePosition> {
        positionsToSell.clear()
        for (position in positionsSelected) {
            positionsToSell.add(PurchasePosition(position))
        }

        for (position in positionsToSell) {
            position.processInitialProfit()
            position.status = PurchaseStatus.WAITING
        }

        return positionsToSell
    }

    public fun getTotalSellString() : String {
        var value = 0.0
        for (position in positionsToSell) {
            value += position.getProfitPrice() * position.position.balance
        }
        return "%.1f".format(value) + "$"
    }

    public fun getTotalPurchasePieces() : Int {
        var value = 0
        for (position in positionsToSell) {
            value += position.position.lots
        }
        return value
    }

    public fun getNotificationTextShort(): String {
        var price = getTotalSellString()
        var tickers = ""
        for (position in positionsToSell) {
            tickers += "${position.position.lots}*${position.position.ticker} "
        }

        return "$price:\n$tickers"
    }

    public fun getNotificationTextLong(): String {
        var tickers = ""
        for (position in positionsToSell) {
            val p = "%.1f".format(position.position.lots * position.getProfitPrice()) + "$"
            tickers += "${position.position.ticker} * ${position.position.lots} шт. = ${p} ${position.getStatusString()}\n"
        }

        return tickers
    }
}