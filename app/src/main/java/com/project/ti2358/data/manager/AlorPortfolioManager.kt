package com.project.ti2358.data.manager

import com.project.ti2358.data.alor.model.*
import com.project.ti2358.data.alor.service.AlorPortfolioService
import com.project.ti2358.data.alor.service.StreamingAlorService
import com.project.ti2358.data.daager.service.ThirdPartyService
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

    private val thirdPartyService: ThirdPartyService by inject()
    private val streamingAlorService: StreamingAlorService by inject()
    private val alorPortfolioService: AlorPortfolioService by inject()

    private var tradeServers: Map<String, List<AlorTradeServer>> = mutableMapOf()
    private var stockServers: List<AlorTradeServer> = mutableListOf()
    private var mainServer: AlorTradeServer? = null

    var orders: MutableList<AlorOrder> = mutableListOf()
    private var stopOrders: MutableList<AlorOrder> = mutableListOf()

    var portfolioPositions: MutableList<AlorPosition> = mutableListOf()

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
            try {
                if (SettingsManager.getAlorUsername() != "") {
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

                    startUpdatePortfolio()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                log(e.printStackTrace().toString())
            }
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
                        1000 * 20 // 20s
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
            mainServer?.let {
                orders = synchronizedList(alorPortfolioService.orders(AlorExchange.SPBX, it.portfolio))
                orders.removeAll { o -> o.status != AlorOrderStatus.WORKING }

                stopOrders = synchronizedList(alorPortfolioService.stoporders(AlorExchange.SPBX, it.portfolio))

                log("ALOR orders = $orders")
                log("ALOR stoporders = $orders")
            }
            baseSortOrders()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    suspend fun refreshDeposit(): Boolean {
        try {
            mainServer?.let {
                portfolioPositions = synchronizedList(alorPortfolioService.positions(AlorExchange.SPBX, it.portfolio))
                baseSortPortfolio()
                log("ALOR positions = $portfolioPositions")
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    suspend fun refreshKotleta() {
        try {
            mainServer?.let {
                summary = alorPortfolioService.summary(AlorExchange.SPBX, it.portfolio)
                money = alorPortfolioService.money(AlorExchange.SPBX, it.portfolio)
                log("ALOR summary = $summary")
                log("ALOR money = $money")
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
    }

    private fun baseSortOrders() {
        orders.sortByDescending { abs(it.qty * it.price) }

        for (order in orders) {
            if (order.stock == null) {
                order.stock = stockManager.getStockByTicker(order.symbol)
            }
        }
    }

    fun getPositions() : List<AlorPosition> {
        return portfolioPositions
    }

    fun getFreeCashEUR(): String {
        var total = 0.0
//        for (currency in currencyPositions) {
//            if (currency.currency == Currency.EUR) {
//                total += currency.balance
//            }
//        }
        val symbols = DecimalFormatSymbols.getInstance()
        symbols.groupingSeparator = ' '
        return DecimalFormat("###,###.##€", symbols).format(total)
    }

    fun getFreeCashUSD(): String {
        var total = 0.0
//        for (currency in currencyPositions) {
//            if (currency.currency == Currency.USD) {
//                total += currency.balance
//            }
//        }
        val symbols = DecimalFormatSymbols.getInstance()
        symbols.groupingSeparator = ' '
        return DecimalFormat("###,###.##$", symbols).format(total)
    }

    fun getFreeCashRUB(): String {
        var total = 0.0
//        for (currency in currencyPositions) {
//            if (currency.currency == Currency.RUB) {
//                total += currency.balance
//            }
//        }
        val symbols = DecimalFormatSymbols.getInstance()
        symbols.groupingSeparator = ' '
        return DecimalFormat("###,###.##₽", symbols).format(total)
    }

    private fun getFreeCash(): Double {
        var total = 0.0
//        for (currency in currencyPositions) {
//            if (currency.currency == Currency.USD) {
//                total += currency.balance
//            }
//            if (currency.currency == Currency.RUB) {
//                total += currency.balance / Utils.getUSDRUB()
//            }
//        }
        return total
    }

    fun getPercentBusyInStocks(): Int {
        val free = getFreeCash()
        var busy = 0.0

        for (position in portfolioPositions) {
            if (position.isCurrency) {
                busy += abs(position.avgPrice * position.getLots())
            }

            if (!position.isCurrency) {
                busy += abs(position.avgPrice * position.getLots() / Utils.getUSDRUB())
            }
        }

        return (busy / (free + busy) * 100).toInt()
    }

    public fun getPositionForTicker(ticker: String): AlorPosition? {
        return portfolioPositions.find { it.symbol == ticker }
    }

    public fun getOrderAllOrdersForTicker(ticker: String, operation: OperationType): List<AlorOrder> {
        return orders.filter { it.symbol == ticker && it.side == operation }
    }

    public fun getOrderForId(id: String, operation: OperationType): AlorOrder? {
        return orders.find { it.id == id && it.side == operation }
    }
}











