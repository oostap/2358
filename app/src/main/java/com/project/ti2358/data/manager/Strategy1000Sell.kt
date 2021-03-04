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
    private var positionsToSell: MutableList<PurchaseStock> = mutableListOf()

    var positionsToSell700: MutableList<PurchaseStock> = mutableListOf()
    var positionsToSell1000: MutableList<PurchaseStock> = mutableListOf()

    fun process(): MutableList<PortfolioPosition> {
        positions = depositManager.portfolioPositions
        positions.sortByDescending { it.lots * it.getAveragePrice() }
        return positions
    }

    fun setSelected(position: PortfolioPosition, value: Boolean) {
        if (value) {
            if (position !in positionsSelected)
                positionsSelected.add(position)
        } else {
            positionsSelected.removeAll { it.figi == position.figi }
        }
        positionsSelected.sortByDescending { it.lots * it.getAveragePrice() }
    }

    fun isSelected(position: PortfolioPosition): Boolean {
        return positionsSelected.find { it.figi == position.figi } != null
    }

    fun processSellPosition(): MutableList<PurchaseStock> {
        positionsToSell.clear()
        for (position in positionsSelected) {
            position.stock?.let {
                val stock = PurchaseStock(it)
                stock.position = position
                positionsToSell.add(stock)
            }
        }

        for (position in positionsToSell) {
            position.processInitialProfit()
            position.status = OrderStatus.WAITING
        }

        return positionsToSell
    }

    fun getTotalPurchasePieces(): Int {
        var value = 0
        for (position in positionsToSell) {
            value += position.position.lots
        }
        return value
    }

    fun getTotalSellString(): String {
        var value = 0.0
        for (position in positionsToSell) {
            value += position.getProfitPriceForSell() * position.position.balance
        }
        return value.toDollar()
    }

    fun getTotalSellString1000(): String {
        var value = 0.0
        for (position in positionsToSell1000) {
            value += position.getProfitPriceForSell() * position.position.balance
        }
        return value.toDollar()
    }

    fun getNotificationTextShort1000(): String {
        val price = getTotalSellString1000()
        var tickers = ""
        for (position in positionsToSell1000) {
            tickers += "${position.position.lots}*${position.position.ticker} "
        }

        return "$price:\n$tickers"
    }

    fun getNotificationTextLong1000(): String {
        var tickers = ""
        for (position in positionsToSell1000) {
            val p = "%.1f$ > %.1f$ > %.1f%%".format(position.position.lots * position.getProfitPriceForSell(), position.getProfitPriceForSell(), position.percentProfitSellFrom)
            tickers += "${position.position.ticker} * ${position.position.lots} шт. = $p ${position.getStatusString()}\n"
        }

        return tickers
    }

    fun getTotalSellString700(): String {
        var value = 0.0
        for (position in positionsToSell700) {
            value += position.getProfitPriceForSell() * position.position.balance
        }
        return value.toDollar()
    }

    fun getNotificationTextShort700(): String {
        val price = getTotalSellString700()
        var tickers = ""
        for (position in positionsToSell700) {
            tickers += "${position.position.lots}*${position.position.ticker} "
        }

        return "$price:\n$tickers"
    }

    fun getNotificationTextLong700(): String {
        var tickers = ""
        for (position in positionsToSell700) {
            val p = "%.1f$ > %.1f$ > %.1f%%".format(position.position.lots * position.getProfitPriceForSell(), position.getProfitPriceForSell(), position.percentProfitSellFrom)
            tickers += "${position.position.ticker} * ${position.position.lots} шт. = $p ${position.getStatusString()}\n"
        }

        return tickers
    }

    fun startSell700() {
        positionsToSell700 = positionsToSell
    }

    fun startSell1000() {
        positionsToSell1000 = positionsToSell
    }
}