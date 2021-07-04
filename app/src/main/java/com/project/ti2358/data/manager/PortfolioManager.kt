package com.project.ti2358.data.manager

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.project.ti2358.R
import com.project.ti2358.TheApplication
import com.project.ti2358.data.model.dto.*
import com.project.ti2358.data.model.dto.Currency
import com.project.ti2358.data.service.OrdersService
import com.project.ti2358.data.service.PortfolioService
import com.project.ti2358.service.Utils
import kotlinx.coroutines.*
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Collections.synchronizedList
import kotlin.math.abs

@KoinApiExtension
class PortfolioManager : KoinComponent {
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
        SettingsManager.preferences = PreferenceManager.getDefaultSharedPreferences(TheApplication.application.applicationContext)
        val key: String = TheApplication.application.applicationContext.getString(R.string.setting_key_tinkoff_account_id)
        val value: String? = SettingsManager.preferences.getString(key, "")

        if (value == "" && accounts.isNotEmpty()) return accounts.first().brokerAccountId

        return value ?: ""
    }

    fun setActiveBrokerAccountId(id: String) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(TheApplication.application.applicationContext)
        val editor: SharedPreferences.Editor = preferences.edit()
        val key = TheApplication.application.applicationContext.getString(R.string.setting_key_tinkoff_account_id)
        editor.putString(key, id)
        editor.apply()
    }

    suspend fun refreshAccounts(): Boolean {
        try {
            accounts = synchronizedList(portfolioService.accounts().accounts)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
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
                total += currency.balance / Utils.getUSDRUB()
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
                busy += abs(position.getAveragePrice() * position.balance / Utils.getUSDRUB())
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

    private suspend fun baseSortPortfolio() = withContext(StockManager.stockContext) {
        portfolioPositions.forEach { it.stock = stocksManager.getStockByFigi(it.figi) }

        portfolioPositions.sortByDescending {
            val multiplier = if (it.stock?.instrument?.currency == Currency.USD) 1.0 else 1.0 / Utils.getUSDRUB()
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
//        val list = portfolioPositions
//        val blacklist = strategyBlacklist.getBlacklistStocks()
//        list.removeAll { it.ticker in blacklist.map { stock -> stock.ticker } }
        return portfolioPositions
    }
}
