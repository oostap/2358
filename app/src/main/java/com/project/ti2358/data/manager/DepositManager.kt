package com.project.ti2358.data.manager

import com.project.ti2358.data.model.dto.*
import com.project.ti2358.data.model.dto.Currency
import com.project.ti2358.data.service.OrdersService
import com.project.ti2358.data.service.PortfolioService
import com.project.ti2358.data.service.ThirdPartyService
import com.project.ti2358.service.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Collections.synchronizedList
import kotlin.math.abs

@KoinApiExtension
class DepositManager : KoinComponent {
    private val stocksManager: StockManager by inject()

    private val portfolioService: PortfolioService by inject()
    private val ordersService: OrdersService by inject()

    var portfolioPositions: MutableList<PortfolioPosition> = synchronizedList(mutableListOf())
    var currencyPositions: MutableList<CurrencyPosition> = synchronizedList(mutableListOf())
    var orders: MutableList<Order> = synchronizedList(mutableListOf())
    var accounts: MutableList<Account> = synchronizedList(mutableListOf())

    private var refreshDepositDelay: Long = 20 * 1000 // 20s

    public fun startUpdatePortfolio() {
        GlobalScope.launch(Dispatchers.Main) {
            while (true) {
                try {
                    if (accounts.isEmpty()) accounts = synchronizedList(portfolioService.accounts().accounts)

                    refreshDeposit()

                    delay(500) // 1s

                    refreshKotleta()

                    delay(500) // 1s

                    refreshOrders()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // ночью делать обновление раз в час
                refreshDepositDelay = when {
                    Utils.isNight() -> {
                        1000 * 60 * 30 // 30m
                    }
                    Utils.isHighSpeedSession() -> {
                        1000 * 5 // 1s
                    }
                    else -> {
                        1000 * 20 // 20s
                    }
                }

                delay(refreshDepositDelay)
            }
        }
    }

    fun getActiveBrokerAccountId(): String {
        val accountType = SettingsManager.getActiveBrokerType()
        for (acc in accounts) {
            if (acc.brokerAccountType == accountType) {
                return acc.brokerAccountId
            }
        }
        return accounts.first().brokerAccountId
    }

    suspend fun cancelOrder(order: Order): Boolean {
        try {
            ordersService.cancel(order.orderId, getActiveBrokerAccountId())
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    suspend fun cancelAllOrders() {
        for (order in orders) {
            try {
                cancelOrder(order)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        refreshOrders()
    }

    suspend fun refreshOrders(): Boolean {
        try {
            if (accounts.isEmpty()) accounts = synchronizedList(portfolioService.accounts().accounts)
            orders = ordersService.orders(getActiveBrokerAccountId()) as MutableList<Order>
            baseSortOrders()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    suspend fun refreshDeposit(): Boolean {
        try {
            if (accounts.isEmpty()) accounts = synchronizedList(portfolioService.accounts().accounts)
            portfolioPositions = synchronizedList(portfolioService.portfolio(getActiveBrokerAccountId()).positions)
            baseSortPortfolio()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    suspend fun refreshKotleta() {
        try {
            currencyPositions = synchronizedList(portfolioService.currencies(getActiveBrokerAccountId()).currencies)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    public fun getFreeCashUSD(): Double {
        for (currency in currencyPositions) {
            if (currency.currency == Currency.USD) {
                return currency.balance
            }
        }
        return 0.0
    }

    public fun getPercentBusyInStocks(): Int {
        val free = getFreeCashUSD()
        var busy = 0.0

        for (position in portfolioPositions) {
            if (position.averagePositionPrice?.currency == Currency.USD) {
                busy += position.getAveragePrice() * position.balance
            }
        }

        return (busy / (free + busy) * 100).toInt()
    }

    public fun getPositionForFigi(figi: String): PortfolioPosition? {
        return portfolioPositions.find { it.figi == figi }
    }

    public fun getOrderForFigi(figi: String): Order? {
        return orders.find { it.figi == figi }
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

    private fun baseSortOrders() {
        orders.sortByDescending { abs(it.requestedLots * it.price) }

        for (order in orders) {
            if (order.stock == null) {
                order.stock = stocksManager.getStockByFigi(order.figi)
            }
        }
    }
}
