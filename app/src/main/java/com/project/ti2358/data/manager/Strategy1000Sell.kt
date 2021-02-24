package com.project.ti2358.data.manager

import com.project.ti2358.data.model.dto.PortfolioPosition
import com.project.ti2358.service.toDollar
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@KoinApiExtension
class Strategy1000Sell() : KoinComponent {
    private val depositManager: DepositManager by inject()

    var positions: MutableList<PortfolioPosition> = mutableListOf()
    var positionsSelected: MutableList<PortfolioPosition> = mutableListOf()
    var positionsToSell: MutableList<PurchasePosition> = mutableListOf()

    fun process(): MutableList<PortfolioPosition> {
        val all = depositManager.portfolioPositions

        positions.clear()
        positions.addAll(all)

        positions.sortByDescending { it.lots * it.getAveragePrice() }
        return positions
    }

    fun setSelected(position: PortfolioPosition, value: Boolean) {
        if (value) {
            positionsSelected.removeAll { it.figi == position.figi }
        } else {
            val filter = positionsSelected.filter { it.figi == position.figi }
            if (filter.isEmpty())
                positionsSelected.add(position)
        }
        positionsSelected.sortByDescending { it.lots * it.getAveragePrice() }
    }

    fun isSelected(position: PortfolioPosition): Boolean {
        val filter = positionsSelected.filter { it.figi == position.figi }
        return filter.isNotEmpty()
    }

    fun processSellPosition(): MutableList<PurchasePosition> {
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

    fun getTotalSellString(): String {
        var value = 0.0
        for (position in positionsToSell) {
            value += position.getProfitPrice() * position.position.balance
        }
        return value.toDollar()
    }

    fun getTotalPurchasePieces(): Int {
        var value = 0
        for (position in positionsToSell) {
            value += position.position.lots
        }
        return value
    }

    fun getNotificationTextShort(): String {
        val price = getTotalSellString()
        var tickers = ""
        for (position in positionsToSell) {
            tickers += "${position.position.lots}*${position.position.ticker} "
        }

        return "$price:\n$tickers"
    }

    fun getNotificationTextLong(): String {
        var tickers = ""
        for (position in positionsToSell) {
            val p =
                "%.1f$ -> %.1f$ -> %.1f".format(position.position.lots * position.getProfitPrice(), position.getProfitPrice(), position.profit) + "%"
            tickers += "${position.position.ticker} * ${position.position.lots} шт. = $p ${position.getStatusString()}\n"
        }

        return tickers
    }
}