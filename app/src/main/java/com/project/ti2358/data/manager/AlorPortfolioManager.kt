package com.project.ti2358.data.manager

import com.project.ti2358.data.alor.model.*
import com.project.ti2358.data.alor.service.AlorPortfolioService
import com.project.ti2358.data.alor.service.StreamingAlorService
import com.project.ti2358.data.daager.service.ThirdPartyService
import com.project.ti2358.service.Utils
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

    private var orders: MutableList<AlorOrder> = mutableListOf()
    private var stopOrders: MutableList<AlorOrder> = mutableListOf()

    private var positions: MutableList<AlorPosition> = mutableListOf()

    private var money: AlorMoney? = null
    private var summary: AlorSummary? = null

    private var refreshDepositDelay: Long = 20 * 1000 // 20s

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
                positions = synchronizedList(alorPortfolioService.positions(AlorExchange.SPBX, it.portfolio))
                log("ALOR positions = $positions")
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

    private fun baseSortOrders() {
        orders.sortByDescending { abs(it.qty * it.price) }

        for (order in orders) {
            if (order.stock == null) {
                order.stock = stockManager.getStockByTicker(order.symbol)
            }
        }
    }
}
