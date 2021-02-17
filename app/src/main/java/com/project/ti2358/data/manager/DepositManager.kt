package com.project.ti2358.data.manager

import com.project.ti2358.data.model.dto.*
import com.project.ti2358.data.model.dto.Currency
import com.project.ti2358.data.service.OrdersService
import com.project.ti2358.data.service.PortfolioService
import com.project.ti2358.data.service.SettingsManager
import com.project.ti2358.service.Utils
import com.project.ti2358.service.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Collections.synchronizedList
import kotlin.math.abs
import kotlin.math.log

@KoinApiExtension
class DepositManager : KoinComponent {
    private val stocksManager: StockManager by inject()

    private val portfolioService: PortfolioService by inject()
    private val ordersService: OrdersService by inject()

    var portfolioPositions: MutableList<PortfolioPosition> = synchronizedList(mutableListOf())
    var currencyPositions: MutableList<CurrencyPosition> = synchronizedList(mutableListOf())
    var orders: MutableList<Order> = synchronizedList(mutableListOf())

    private var refreshDepositDelay: Long = 20 * 1000 // 20s

    public fun startUpdatePortfolio() {
        GlobalScope.launch(Dispatchers.Main) {
            while (true) {
                try {
                    refreshDeposit()

                    delay(500) // 1s

                    currencyPositions = synchronizedList(portfolioService.currencies().currencies)

                    delay(500) // 1s

                    orders = ordersService.orders() as MutableList<Order>
                    for (order in orders) {
                        ordersService.cancel(order.orderId)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // ночью делать обновление раз в час
                if (Utils.isNight()) {
                    refreshDepositDelay = 1000 * 60 * 30 // 30m
                } else if (Utils.isHighSpeedSession()) {
                    refreshDepositDelay = 1000 * 1 // 1s
                } else {
                    refreshDepositDelay = 1000 * 20 // 20s
                }

                delay(refreshDepositDelay)
            }
        }
    }

    suspend fun refreshDeposit() {
        try {
            portfolioPositions = synchronizedList(portfolioService.portfolio().positions)
            baseSortPortfolio()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    public fun getFreeCashUSD(): String {
        for (currency in currencyPositions) {
            if (currency.currency == Currency.USD) {
                return "${currency.balance} $"
            }
        }
        return ""
    }

    public fun getPositionForFigi(figi: String): PortfolioPosition? {
        return portfolioPositions.find { it.figi == figi }
    }

    private fun baseSortPortfolio() {
        portfolioPositions.sortByDescending { abs(it.lots * it.getAveragePrice()) }

        // удалить позицию $
        portfolioPositions.removeAll { it.ticker.contains("USD000") }

        for (position in portfolioPositions) {
            if (position.stock == null) {
                position.stock = stocksManager.getStockByFigi(position.figi)
            }
        }
    }
}
