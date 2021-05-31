package com.project.ti2358.data.manager

import com.project.ti2358.data.model.dto.LimitOrder
import com.project.ti2358.data.model.dto.OperationType
import com.project.ti2358.data.service.OperationsService
import com.project.ti2358.data.service.OrdersService
import com.project.ti2358.service.Utils
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.lang.Exception
import kotlin.math.abs
import kotlin.math.roundToInt

@KoinApiExtension
class StrategyFollower : KoinComponent {
    private val stockManager: StockManager by inject()
    private val depositManager: DepositManager by inject()
    private val ordersService: OrdersService by inject()
    private val orderbookManager: OrderbookManager by inject()
    private var moneySpent: Double = 0.0

    private var jobRefreshDeposit: Job? = null

    var started: Boolean = false

    fun processStrategy(userId: Long, command: String): Boolean {
        if (!started) return false

        val followIds = SettingsManager.getFollowerIds()
        if (userId !in followIds) return false

        val list = command.split(" ")

        try {
            val operation = list[1]
            val ticker = list[2]
            val stock = stockManager.getStockByTicker(ticker)
            val figi = stock?.figi ?: ""
            if (figi == "") return false

            if (operation in listOf("BUY", "SELL")) {
                if (list.size != 5) return false
                val price = list[3].toDouble()
                val percent = list[4].toInt()
                if (price == 0.0 || percent == 0) return false

                if (operation == "BUY") {   // # BUY VIPS 29.46 1
                    val moneyPart = SettingsManager.getFollowerPurchaseVolume() / 100.0 * percent
                    val lots = (moneyPart / price).toInt()

                    // недостаточно для покупки даже одного лота
                    if (lots == 0 || moneyPart == 0.0) return false

                    // превышен лимит торговли по сигналам
                    if (abs(moneySpent - lots * price) > SettingsManager.getFollowerPurchaseVolume()) return false

                    GlobalScope.launch(Dispatchers.Main) {
                        try {
                            ordersService.placeLimitOrder(
                                lots,
                                figi,
                                price,
                                OperationType.BUY,
                                depositManager.getActiveBrokerAccountId()
                            )
                            Utils.showToastAlert("$ticker создан новый ордер: ПОКУПКА!")
                        } catch (e: Exception) {

                        }
                    }
                    moneySpent -= lots * price
                }

                if (operation == "SELL") {  // # SELL VIPS 29.46 1
                    val position = depositManager.getPositionForFigi(figi) ?: return false
                    val lots = (position.lots / 100.0 * percent).toInt()

                    if (lots == 0) return false

                    // сильно большая поза, не сдавать
                    if (lots * price > SettingsManager.getFollowerPurchaseVolume() || position.lots * price > SettingsManager.getFollowerPurchaseVolume()) return false

                    GlobalScope.launch(Dispatchers.Main) {
                        try {
                            ordersService.placeLimitOrder(
                                lots,
                                figi,
                                price,
                                OperationType.SELL,
                                depositManager.getActiveBrokerAccountId()
                            )
                            Utils.showToastAlert("$ticker создан новый ордер: ПРОДАЖА!")
                        } catch (e: Exception) {

                        }
                    }
                    moneySpent += lots * price
                }
            }

            if (operation in listOf("BUY_MOVE", "SELL_MOVE")) { // # BUY_MOVE VIPS 0.01
                if (list.size != 4) return false

                val change = list[3].toDouble()
                val operationType = if ("SELL" in operation) OperationType.SELL else OperationType.BUY
                val buyOrders = depositManager.getOrderAllOrdersForFigi(figi, operationType)
                buyOrders.forEach { order ->
                    val newIntPrice = ((order.price + change) * 100).roundToInt()
                    val newPrice: Double = Utils.makeNicePrice(newIntPrice / 100.0, order.stock)
                    orderbookManager.replaceOrder(order, newPrice, operationType)
                }
            }

            if (operation in listOf("BUY_CANCEL", "SELL_CANCEL")) { // # BUY_CANCEL VIPS
                if (list.size != 3) return false

                val operationType = if ("SELL" in operation) OperationType.SELL else OperationType.BUY
                val buyOrders = depositManager.getOrderAllOrdersForFigi(figi, operationType)
                buyOrders.forEach { order ->
                    orderbookManager.cancelOrder(order)

                    val money = (order.requestedLots - order.executedLots) * order.price
                    if (operationType == OperationType.SELL) {
                        moneySpent += money
                    } else {
                        moneySpent -= money
                    }
                }
            }

//            if (operation == "STATUS") { // # STATUS VIPS
//                val position = depositManager.getPositionForFigi(figi) ?: return false
//
//            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return false
    }

    fun startStrategy() {
        started = true

        jobRefreshDeposit?.cancel()
        jobRefreshDeposit = GlobalScope.launch(Dispatchers.Main) {
            while (true) {
                delay(5000)
                depositManager.refreshDeposit()
                depositManager.refreshOrders()
            }
        }
    }

    fun stopStrategy() {
        started = false
        jobRefreshDeposit?.cancel()
    }
}