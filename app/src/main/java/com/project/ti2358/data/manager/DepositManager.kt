package com.project.ti2358.data.manager

import com.project.ti2358.data.model.dto.*
import com.project.ti2358.data.model.dto.Currency
import com.project.ti2358.data.service.OrdersService
import com.project.ti2358.data.service.PortfolioService
import com.project.ti2358.service.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*
import java.util.Collections.synchronizedList
import kotlin.math.abs

@KoinApiExtension
class DepositManager : KoinComponent {
    private val stocksManager: StockManager by inject()

    private val portfolioService: PortfolioService by inject()
    private val ordersService: OrdersService by inject()
    private val strategyBlacklist: StrategyBlacklist by inject()

    var portfolioPositions: MutableList<PortfolioPosition> = synchronizedList(mutableListOf())
    var currencyPositions: MutableList<CurrencyPosition> = synchronizedList(mutableListOf())
    var orders: MutableList<Order> = synchronizedList(mutableListOf())
    var accounts: MutableList<Account> = synchronizedList(mutableListOf())

    private var refreshDepositDelay: Long = 20 * 1000 // 20s

    public fun startUpdatePortfolio() {
        GlobalScope.launch(Dispatchers.Default) {
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

    suspend fun refreshOrders(): Boolean {
        try {
            if (accounts.isEmpty()) accounts = synchronizedList(portfolioService.accounts().accounts)
            orders = synchronizedList(ordersService.orders(getActiveBrokerAccountId()))
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

    fun getFreeCashEUR(): String {
        var total = 0.0
        for (currency in currencyPositions) {
            if (currency.currency == Currency.EUR) {
                total += currency.balance
            }
        }
        val symbols = DecimalFormatSymbols.getInstance()
        symbols.groupingSeparator = ' '
        return DecimalFormat("###,###.##€", symbols).format(total)
    }

    fun getFreeCashUSD(): String {
        var total = 0.0
        for (currency in currencyPositions) {
            if (currency.currency == Currency.USD) {
                total += currency.balance
            }
        }
        val symbols = DecimalFormatSymbols.getInstance()
        symbols.groupingSeparator = ' '
        return DecimalFormat("###,###.##$", symbols).format(total)
    }

    fun getFreeCashRUB(): String {
        var total = 0.0
        for (currency in currencyPositions) {
            if (currency.currency == Currency.RUB) {
                total += currency.balance
            }
        }
        val symbols = DecimalFormatSymbols.getInstance()
        symbols.groupingSeparator = ' '
        return DecimalFormat("###,###.##₽", symbols).format(total)
    }

    private fun getFreeCash(): Double {
        var total = 0.0
        for (currency in currencyPositions) {
            if (currency.currency == Currency.USD) {
                total += currency.balance
            }
            if (currency.currency == Currency.RUB) {
                total += currency.balance / 74.0        // todo: взять реальную цену
            }
        }
        return total
    }

    fun getPercentBusyInStocks(): Int {
        val free = getFreeCash()
        var busy = 0.0

        for (position in portfolioPositions) {
            if (position.averagePositionPrice?.currency == Currency.USD) {
                busy += abs(position.getAveragePrice() * position.balance)
            }

            if (position.averagePositionPrice?.currency == Currency.RUB) {
                busy += abs(position.getAveragePrice() * position.balance / 74.0)    // todo: взять реальную цену
            }
        }

        return (busy / (free + busy) * 100).toInt()
    }

    public fun getPositionForFigi(figi: String): PortfolioPosition? {
        return portfolioPositions.find { it.figi == figi }
    }

    public fun getOrderForFigi(figi: String, operation: OperationType): Order? {
        return orders.find { it.figi == figi && it.operation == operation }
    }

    public fun getOrderAllOrdersForFigi(figi: String, operation: OperationType): List<Order> {
        return orders.filter { it.figi == figi && it.operation == operation }
    }

    private fun baseSortPortfolio() {
        portfolioPositions.forEach { it.stock = stocksManager.getStockByFigi(it.figi) }

        portfolioPositions.sortByDescending {
            val multiplier = if (it.stock?.instrument?.currency == Currency.USD) 1.0 else 1.0 / 74.0 // todo: взять реальную цену
            abs(it.lots * it.getAveragePrice() * multiplier)
        }

        // удалить все НЕ акции
        portfolioPositions.removeAll { it.instrumentType != InstrumentType.STOCK }

        // удалить позицию $
        portfolioPositions.removeAll { "USD000" in it.ticker }
    }

    private fun baseSortOrders() {
        orders.sortByDescending { abs(it.requestedLots * it.price) }

        for (order in orders) {
            if (order.stock == null) {
                order.stock = stocksManager.getStockByFigi(order.figi)
            }
        }
    }

    fun getPositions() : List<PortfolioPosition> {
        val list = portfolioPositions
        val blacklist = strategyBlacklist.getBlacklistStocks()
        list.removeAll { it.ticker in blacklist.map { stock -> stock.ticker } }
        return list
    }
}
