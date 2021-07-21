package com.project.ti2358.data.manager

import com.project.ti2358.data.alor.model.*
import com.project.ti2358.data.alor.service.AlorPortfolioService
import com.project.ti2358.data.tinkoff.model.*
import com.project.ti2358.data.tinkoff.model.Currency
import com.project.ti2358.service.Utils
import com.project.ti2358.service.log
import kotlinx.coroutines.*
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Collections.synchronizedList
import java.util.Collections.synchronizedMap
import kotlin.math.abs

@KoinApiExtension
class AlorPortfolioManager : KoinComponent {
    private val stockManager: StockManager by inject()
    private val alorAuthManager: AlorAuthManager by inject()

    private val alorPortfolioService: AlorPortfolioService by inject()

    private var tradeServers: Map<String, List<AlorTradeServer>> = mutableMapOf()
    private var stockServers: List<AlorTradeServer> = mutableListOf()
    private var mainServer: AlorTradeServer? = null

    var orders: MutableList<AlorOrder> = mutableListOf()
    private var stopOrders: MutableList<AlorOrder> = mutableListOf()

    var portfolioPositions: MutableList<PositionAlor> = mutableListOf()
    var positionUSD: PositionAlor? = null

    var portfolioPositionsMOEX: MutableList<PositionAlor> = mutableListOf()
    var positionRUB: PositionAlor? = null

    private var money: AlorMoney? = null
    private var summary: AlorSummary? = null

    private var refreshDepositDelay: Long = 20 * 1000 // 20s

    companion object {
        var PORTFOLIO: String = ""
        var ACCOUNT: String = ""
    }

    fun start() {
        GlobalScope.launch(Dispatchers.Default) {
            // обновить токен
            alorAuthManager.refreshToken()

            // загрузить сервера портфелей
            if (SettingsManager.getAlorUsername() != "") {
                refreshAccounts()

                startUpdatePortfolio()
            }

            alorAuthManager.startRefreshToken()
        }
    }

    private fun startUpdatePortfolio() {
        GlobalScope.launch(Dispatchers.Default) {
            while (true) {
                try {
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
                        1000 * 30 // 20s
                    }
                }

                delay(refreshDepositDelay)
            }
        }
    }

    suspend fun refreshAccounts(): Boolean {
        try {
            tradeServers = synchronizedMap(alorPortfolioService.portfolios(SettingsManager.getAlorUsername()))
            log("ALOR $tradeServers")

            if (tradeServers.containsKey("Фондовый рынок")) {
                stockServers = tradeServers["Фондовый рынок"]!!

                if (stockServers.isNotEmpty()) {
                    mainServer = stockServers.first()
                    PORTFOLIO = mainServer?.portfolio ?: ""
                    ACCOUNT = mainServer?.tks ?: ""
                }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    suspend fun refreshOrders(): Boolean {
        try {
            if (mainServer != null) {
                orders = synchronizedList(alorPortfolioService.orders(AlorExchange.SPBX, mainServer!!.portfolio))
                orders.removeAll { o -> o.status != AlorOrderStatus.WORKING }

                stopOrders = synchronizedList(alorPortfolioService.stoporders(AlorExchange.SPBX, mainServer!!.portfolio))

                log("ALOR orders = $orders")
                log("ALOR stoporders = $orders")

                baseSortOrders()
            } else {
                start()
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    suspend fun refreshDeposit(): Boolean {
        try {
            if (mainServer != null) {
                portfolioPositions = synchronizedList(alorPortfolioService.positions(AlorExchange.SPBX, mainServer!!.portfolio))
                positionUSD = portfolioPositions.find { it.symbol == "USD" }
                baseSortPortfolio()

                portfolioPositionsMOEX = synchronizedList(alorPortfolioService.positions(AlorExchange.MOEX, mainServer!!.portfolio))
                positionRUB = portfolioPositionsMOEX.find { it.symbol == "RUB" }

                log("ALOR positions = $portfolioPositions")
            } else {
                start()
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    suspend fun refreshKotleta() {
        try {
            if (mainServer != null) {
                summary = alorPortfolioService.summary(AlorExchange.SPBX, mainServer!!.portfolio)
                money = alorPortfolioService.money(AlorExchange.SPBX, mainServer!!.portfolio)
                log("ALOR summary = $summary")
                log("ALOR money = $money")
            } else {
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun baseSortPortfolio() = withContext(StockManager.stockContext) {
        portfolioPositions.forEach { it.stock = stockManager.getStockByTicker(it.symbol) }

        portfolioPositions.sortByDescending {
            val multiplier = if (it.stock?.instrument?.currency == Currency.USD) 1.0 else 1.0 / Utils.getUSDRUB()
            abs(it.getLots() * it.avgPrice * multiplier)
        }

        // удалить позицию $
        portfolioPositions.removeAll { "USD" in it.symbol }

        // удалить нулевые позиции
        portfolioPositions.removeAll { it.getLots() == 0 }
    }

    private fun baseSortOrders() {
        orders.sortByDescending { abs(it.qty * it.price) }

        for (order in orders) {
            if (order.stock == null) {
                order.stock = stockManager.getStockByTicker(order.symbol)
            }
        }
    }

    fun getPositions() : List<PositionAlor> {
        return portfolioPositions
    }

    fun getFreeCashUSD(): String {
        val total = positionUSD?.qtyUnits ?: 0.0
        val symbols = DecimalFormatSymbols.getInstance()
        symbols.groupingSeparator = ' '
        return DecimalFormat("###,###.##$", symbols).format(total)
    }

    fun getFreeCashRUB(): String {
        val total = positionRUB?.qtyUnits ?: 0.0
        val symbols = DecimalFormatSymbols.getInstance()
        symbols.groupingSeparator = ' '
        return DecimalFormat("###,###.##₽", symbols).format(total)
    }

    private fun getFreeCash(): Double {
        return (positionUSD?.qtyUnits ?: 0.0) + ((positionRUB?.qtyUnits ?: 0.0) / Utils.getUSDRUB())
    }

    fun getPercentBusyInStocks(): Int {
        val free = getFreeCash()
        var busy = 0.0

        for (position in portfolioPositions) {
            busy += abs(position.avgPrice * position.getLots())
        }

        return (busy / (free + busy) * 100).toInt()
    }

    public fun getPositionForStock(stock: Stock): PositionAlor? {
        return portfolioPositions.find { it.symbol == stock.ticker }
    }

    public fun getOrderAllForStock(stock: Stock, operation: OperationType): List<AlorOrder> {
        return orders.filter { it.symbol == stock.ticker && it.side == operation }
    }

    public fun getOrderForId(id: String, operation: OperationType): AlorOrder? {
        return orders.find { it.id == id && it.side == operation }
    }
}











