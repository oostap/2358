package com.project.ti2358.data.service

import com.project.ti2358.data.model.dto.*
import com.project.ti2358.data.model.dto.Currency
import com.project.ti2358.service.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*
import java.util.Collections.synchronizedList
import kotlin.math.round

@KoinApiExtension
class DepoManager : KoinComponent {
    private val stocksManager: StockManager by inject()

    private val portfolioService: PortfolioService by inject()
    private val ordersService: OrdersService by inject()
    private val marketService: MarketService by inject()
    private val operationsService: OperationsService by inject()

    var portfolioPositions: MutableList<PortfolioPosition> = synchronizedList(mutableListOf())
    var currencyPositions: MutableList<CurrencyPosition> = synchronizedList(mutableListOf())
    var orders: MutableList<Order> = synchronizedList(mutableListOf())

    public fun startUpdatePortfolio() {
        GlobalScope.launch(Dispatchers.Main) {
            while (true) {
                try {
                    portfolioPositions = synchronizedList(portfolioService.portfolio().positions)
                    baseSortPortfolio()

                    delay(1000) // 1 sec

                    currencyPositions = synchronizedList(portfolioService.currencies().currencies)

                    delay(1000) // 1 sec

                    orders = ordersService.orders() as MutableList<Order>

//                    log(orders.toString())

                } catch (e: Exception) {
                    e.printStackTrace()
                }

                delay(1 * 5 * 1000) // 5 sec
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
        portfolioPositions.sortByDescending { it.lots * it.getAveragePrice() }

        // удалить позицию $
        portfolioPositions.removeAll { it.ticker.contains("USD000") }

        for (posititon in portfolioPositions) {
            if (posititon.stock == null) {
                posititon.stock = stocksManager.getStockByFigi(posititon.figi)
            }
        }
    }
}
