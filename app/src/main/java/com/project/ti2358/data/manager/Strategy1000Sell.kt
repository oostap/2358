package com.project.ti2358.data.manager

import com.project.ti2358.data.model.dto.PortfolioPosition
import com.project.ti2358.service.toMoney
import kotlinx.coroutines.Job
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.lang.Exception
import kotlin.math.abs

@KoinApiExtension
class Strategy1000Sell() : KoinComponent {
    private val depositManager: DepositManager by inject()

    var positions: MutableList<PortfolioPosition> = mutableListOf()
    var positionsSelected: MutableList<PortfolioPosition> = mutableListOf()
    private var purchaseToSell: MutableList<PurchaseStock> = mutableListOf()

    var positionsToSell700: MutableList<PurchaseStock> = mutableListOf()
    var positionsToSell1000: MutableList<PurchaseStock> = mutableListOf()

    var job1000: MutableList<Job?> = mutableListOf()
    var job700: MutableList<Job?> = mutableListOf()

    var started700: Boolean = false
    var started1000: Boolean = false

    fun process(): MutableList<PortfolioPosition> {
        positions = depositManager.portfolioPositions
        positions.sortByDescending { abs(it.lots * it.getAveragePrice()) }
        return positions
    }

    fun setSelected(position: PortfolioPosition, value: Boolean) {
        if (value) {
            if (position !in positionsSelected)
                positionsSelected.add(position)
        } else {
            positionsSelected.removeAll { it.figi == position.figi }
        }
        positionsSelected.sortByDescending { abs(it.lots * it.getAveragePrice()) }
    }

    fun isSelected(position: PortfolioPosition): Boolean {
        return positionsSelected.find { it.figi == position.figi } != null
    }

    fun processSellPosition(): MutableList<PurchaseStock> {
        val purchases: MutableList<PurchaseStock> = mutableListOf()
        for (pos in positionsSelected) {
            pos.stock?.let { stock ->
                val purchase = PurchaseStock(stock)
                for (p in purchaseToSell) {
                    if (p.stock.instrument.ticker == stock.instrument.ticker) {
                        purchase.apply {
                            percentProfitSellFrom = p.percentProfitSellFrom
                        }
                        break
                    }
                }

                purchase.apply {
                    position = pos
                }
                purchases.add(purchase)
            }
        }
        purchaseToSell = purchases

        purchaseToSell.forEach {
            if (it.percentProfitSellFrom == 0.0) {
                it.processInitialProfit()
            }
            it.status = OrderStatus.WAITING
        }
        return purchaseToSell
    }

    fun getTotalPurchasePieces(): Int {
        var value = 0
        for (position in purchaseToSell) {
            value += position.position.lots
        }
        return value
    }

    fun getTotalSellString(): String {
        var value = 0.0
        for (position in purchaseToSell) {
            value += position.getProfitPriceForSell() * position.position.balance
        }
        return value.toMoney(null)
    }

    fun getTotalSellString(positions: MutableList<PurchaseStock>): String {
        var value = 0.0
        for (position in positions) {
            value += position.getProfitPriceForSell() * position.position.balance
        }
        return value.toMoney(null)
    }

    fun getNotificationTextShort(positions: MutableList<PurchaseStock>): String {
        val price = getTotalSellString(positions)
        var tickers = ""
        for (position in positions) {
            tickers += "${position.position.lots}*${position.position.ticker} "
        }

        return "$price:\n$tickers"
    }

    fun getNotificationTextLong(positions: MutableList<PurchaseStock>): String {
        var tickers = ""
        for (position in positions) {
            val p = "%.1f$ > %.2f$ > %.1f%%".format(position.position.lots * position.getProfitPriceForSell(), position.getProfitPriceForSell(), position.percentProfitSellFrom)
            tickers += "${position.position.ticker} * ${position.position.lots} = $p ${position.getStatusString()}\n"
        }

        return tickers
    }

    fun prepareSell700() {
        started700 = false
        positionsToSell700 = purchaseToSell
    }

    fun prepareSell1000() {
        started1000 = false
        positionsToSell1000 = purchaseToSell
    }

    fun startStrategy700Sell() {
        if (started700) return
        started700 = true
        positionsToSell700.forEach {
            job700.add(it.sell())
        }
    }

    fun startStrategy1000Sell() {
        if (started1000) return
        started1000 = true
        positionsToSell1000.forEach {
            job1000.add(it.sell())
        }
    }

    fun stopStrategy700() {
        job700.forEach {
            try {
                if (it?.isActive == true) {
                    it.cancel()
                }
            } catch (e: Exception) {

            }
        }
        job700.clear()
    }

    fun stopStrategy1000() {
        job1000.forEach {
            try {
                if (it?.isActive == true) {
                    it.cancel()
                }
            } catch (e: Exception) {

            }
        }
        job1000.clear()
    }
}