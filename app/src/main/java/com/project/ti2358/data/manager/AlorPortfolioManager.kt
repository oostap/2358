package com.project.ti2358.data.manager

import com.project.ti2358.data.alor.model.AlorExchange
import com.project.ti2358.data.alor.model.AlorOrder
import com.project.ti2358.data.alor.model.AlorTradeServer
import com.project.ti2358.data.alor.service.AlorPortfolioService
import com.project.ti2358.data.alor.service.StreamingAlorService
import com.project.ti2358.data.daager.service.ThirdPartyService
import com.project.ti2358.service.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@KoinApiExtension
class AlorPortfolioManager : KoinComponent {
    private val alorAuthManager: AlorAuthManager by inject()

    private val thirdPartyService: ThirdPartyService by inject()
    private val streamingAlorService: StreamingAlorService by inject()
    private val alorPortfolioService: AlorPortfolioService by inject()

    private var tradeServers: Map<String, List<AlorTradeServer>> = mutableMapOf()
    private var stockServers: List<AlorTradeServer> = mutableListOf()
    private var mainServer: AlorTradeServer? = null
    private var orders: List<AlorOrder> = mutableListOf()
    private var stopOrders: List<AlorOrder> = mutableListOf()

    fun start() {
        GlobalScope.launch(Dispatchers.Default) {
            // обновить токен
            alorAuthManager.refreshToken()

            // загрузить сервера портфелей
            try {
                if (SettingsManager.getAlorUsername() != "") {
                    tradeServers = alorPortfolioService.portfolios(SettingsManager.getAlorUsername())
                    log("ALOR $tradeServers")

                    if (tradeServers.containsKey("Фондовый рынок")) {
                        stockServers = tradeServers["Фондовый рынок"]!!
                    }

                    if (stockServers.isNotEmpty()) {
                        mainServer = stockServers.first()
                    }

                    mainServer?.let {
                        orders = alorPortfolioService.orders(AlorExchange.SPBX, it.portfolio)
                        log("ALOR $orders")

                        stopOrders = alorPortfolioService.stoporders(AlorExchange.SPBX, it.portfolio)
                        log("ALOR $stopOrders")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                log(e.printStackTrace().toString())
            }
        }
    }
}
