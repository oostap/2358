package com.project.ti2358.data.service

import com.project.ti2358.data.model.dto.*
import com.project.ti2358.data.model.dto.Currency
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

    public fun startUpdatePortfolio() {
        GlobalScope.launch(Dispatchers.Main) {
            while (true) {
                try {
                    portfolioPositions = synchronizedList(portfolioService.portfolio().positions)
                    baseSortPortfolio()

                    delay(1000) // 1s

                    currencyPositions = synchronizedList(portfolioService.currencies().currencies)

                    delay(1000) // 1s

                    orders = ordersService.orders() as MutableList<Order>

                } catch (e: Exception) {
                    e.printStackTrace()
                }

                delay(5000) // 5s
            }
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
