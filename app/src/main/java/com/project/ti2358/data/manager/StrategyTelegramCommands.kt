package com.project.ti2358.data.manager

import com.project.ti2358.data.tinkoff.model.OperationType
import com.project.ti2358.data.tinkoff.service.OrdersService
import com.project.ti2358.service.Utils
import kotlinx.coroutines.*
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.lang.Exception
import kotlin.math.abs
import kotlin.math.roundToInt

@KoinApiExtension
class StrategyTelegramCommands : KoinComponent {
    private val stockManager: StockManager by inject()
    private val portfolioManager: PortfolioManager by inject()
    private val ordersService: OrdersService by inject()
    private val brokerManager: BrokerManager by inject()
    private val strategyTelegram: StrategyTelegram by inject()

    private val strategyTazik: StrategyTazik by inject()
    private val strategyTazikEndless: StrategyTazikEndless by inject()
    private val strategyZontikEndless: StrategyZontikEndless by inject()
    private val strategyRocket: StrategyRocket by inject()
    private val strategyTrend: StrategyTrend by inject()
    private val strategyLimits: StrategyLimits by inject()
    private val strategy2358: Strategy2358 by inject()
    private val strategyDayLow: StrategyDayLow by inject()
    private val strategy2225: Strategy2225 by inject()
    private val strategyArbitration: StrategyArbitration by inject()

    private var moneySpent: Double = 0.0

    private var jobRefreshDeposit: Job? = null

    var started: Boolean = false

    fun processInfoCommand(command: String, messageId: Long) : Boolean {
        try {
            val list = command.split(" ")

            val pulseWords = listOf("пульс", "резать", "лось", "хомяк", "пастух", "аллигатор", "профит", "трейд", "бабло", "теханал")
            var contains = false
            pulseWords.forEach {
                if (it in command) {
                    contains = true
                    return@forEach
                }
            }
            val operation = list[0].toLowerCase()

            if (operation == "top") { // топ отросших бумаг от закрытия
                var count = 10
                if (list.size == 2) count = list[1].toInt()
                val all = stockManager.getWhiteStocks()
                all.removeAll { it.getPrice2300() == 0.0 }
                all.sortByDescending { it.changePrice2300DayPercent }

                if (Utils.isMorningSession()) {
                    all.removeAll { it.morning == null }
                }

                strategyTelegram.sendTop(all, count)
                return true
            } else if (operation == "bot") { // топ отросших бумаг от закрытия
                var count = 10
                if (list.size == 2) count = list[1].toInt()
                val all = stockManager.getWhiteStocks()
                all.removeAll { it.getPrice2300() == 0.0 }
                all.sortBy { it.changePrice2300DayPercent }

                if (Utils.isMorningSession()) {
                    all.removeAll { it.morning == null }
                }

                strategyTelegram.sendTop(all, count)
                return true
            } else if (operation == "arb") { // топ отросших бумаг от закрытия
                var count = 10
                if (list.size == 2) count = list[1].toInt()
                val all = strategyArbitration.stocks.toMutableList()
                all.removeAll { it.askPriceRU == 0.0 || it.changePriceArbLongPercent <= 0.0}
                all.sortByDescending { it.changePriceArbLongPercent }

                if (Utils.isMorningSession()) {
                    all.removeAll { it.morning == null }
                }

                strategyTelegram.sendArb(true, all, count)
                return true
            } else if (operation == "arbs") { // топ отросших бумаг от закрытия
                var count = 10
                if (list.size == 2) count = list[1].toInt()
                val all = strategyArbitration.stocks.toMutableList()
                all.removeAll { it.bidPriceRU == 0.0 || it.changePriceArbShortPercent <= 0.0 }
                all.sortByDescending { it.changePriceArbShortPercent }

                if (Utils.isMorningSession()) {
                    all.removeAll { it.morning == null }
                }

                all.removeAll { it.short == null }

                strategyTelegram.sendArb(false, all, count)
                return true
            } else if (operation == "dayhigh") { // топ отросших бумаг от закрытия
                var count = 10
                var high = 0.0
                if (list.size >= 2) count = list[1].toInt()
                if (list.size >= 3) high = list[2].toDouble()

                val all = strategyDayLow.processHigh()
                all.removeAll { it.getPrice2300() == 0.0 }
                all.removeAll { it.changePrice2300DayPercent < high }

                if (Utils.isMorningSession()) {
                    all.removeAll { it.morning == null }
                }

                strategyTelegram.sendDayHigh(all, count)
                return true
            } else if (operation == "daylow") { // топ отросших бумаг от закрытия
                var count = 10
                var low = 0.0
                if (list.size >= 2) count = list[1].toInt()
                if (list.size >= 3) low = list[2].toDouble()

                val all = strategyDayLow.process()
                all.removeAll { it.getPrice2300() == 0.0 }
                all.removeAll { it.changePrice2300DayPercent > low }

                if (Utils.isMorningSession()) {
                    all.removeAll { it.morning == null }
                }

                strategyTelegram.sendDayLow(all, count)
                return true
            }

            val stock = stockManager.getStockByTicker(list[0].toUpperCase())

            if (contains) {
                strategyTelegram.sendPulse(messageId)
                return false
            } else if (list.size == 2) {
                val ticker = list[0].toUpperCase()
                val operation = list[1].toLowerCase()
                val stock = stockManager.getStockByTicker(ticker)

                if (stock != null) {
                    if (operation == "limits") { // # LIMIT UP/DOWN
                        strategyTelegram.sendStockInfo(stock)
                        return false
                    }
                }
            }

            if (stock != null) {
                strategyTelegram.sendStock(stock)
                return false
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    fun processActiveCommand(userId: Long, command: String): Int {
        if (!started) return 0

        val list = command.split(" ")

        val followIds = SettingsManager.getFollowerIds()
        if (userId !in followIds) return 0

        try {
            val operation = list[1].toLowerCase()
            var ticker = ""

            if (list.size > 2) {
                ticker = list[2].toUpperCase()
            }

            val delay = 100L

            if (operation == "depo" && SettingsManager.getTelegramAllowShowDepo()) {
                GlobalScope.launch(Dispatchers.Main) {
                    strategyTelegram.sendDepo()
                }
                return 2
            }

            if (operation == "restart") {
                if (ticker == "ALL") {
                    GlobalScope.launch(Dispatchers.Main) {
                        stockManager.reloadClosePrices()
                        delay(delay)
                        strategyTazikEndless.restartStrategy()
                        delay(delay)
                        strategyRocket.restartStrategy()
                        delay(delay)
                        strategyTrend.restartStrategy()
                        delay(delay)
                        strategyLimits.restartStrategy()
                        delay(delay)
                        strategyZontikEndless.restartStrategy()
                        delay(delay)
                        strategyArbitration.restartStrategy()
                        delay(delay)
                        strategyTazik.restartStrategy()
                    }
                } else if (ticker == "TAZ") {
                    var percent = 0.0
                    var profit = 0.0
                    if (list.size >= 4) {
                        percent = list[3].toDouble()
                    }
                    if (list.size >= 5) {
                        profit = list[4].toDouble()
                    }

                    GlobalScope.launch(Dispatchers.Main) {
                        strategyTazikEndless.restartStrategy(percent, profit)
                    }
                } else if (ticker == "ROCKET") {
                    GlobalScope.launch(Dispatchers.Main) {
                        strategyRocket.restartStrategy()
                    }
                } else if (ticker == "TREND") {
                    GlobalScope.launch(Dispatchers.Main) {
                        strategyTrend.restartStrategy()
                    }
                } else if (ticker == "LIMIT") {
                    GlobalScope.launch(Dispatchers.Main) {
                        strategyLimits.restartStrategy()
                    }
                } else if (ticker == "ARB") {
                    GlobalScope.launch(Dispatchers.Main) {
                        strategyArbitration.restartStrategy()
                    }
                } else if (ticker == "ZONT") {
                    var percent = 0.0
                    var profit = 0.0
                    if (list.size >= 4) {
                        percent = list[3].toDouble()
                    }
                    if (list.size >= 5) {
                        profit = list[4].toDouble()
                    }

                    GlobalScope.launch(Dispatchers.Main) {
                        strategyZontikEndless.restartStrategy(percent, profit)
                    }
                }
                return 2
            } else if (operation == "stop") {
                if (ticker == "ALL") {
                    GlobalScope.launch(Dispatchers.Main) {
                        strategyTazikEndless.stopStrategyCommand()
                        delay(delay)
                        strategyRocket.stopStrategyCommand()
                        delay(delay)
                        strategyTrend.stopStrategyCommand()
                        delay(delay)
                        strategyLimits.stopStrategyCommand()
                        delay(delay)
                        strategyZontikEndless.stopStrategyCommand()
                        delay(delay)
                        strategyArbitration.stopStrategyCommand()
                        delay(delay)
                        strategyTazik.stopStrategyCommand()
                    }
                } else if (ticker == "TAZ") {
                    GlobalScope.launch(Dispatchers.Main) {
                        strategyTazikEndless.stopStrategyCommand()
                    }
                } else if (ticker == "ZONT") {
                    GlobalScope.launch(Dispatchers.Main) {
                        strategyZontikEndless.stopStrategyCommand()
                    }
                } else if (ticker == "ROCKET") {
                    GlobalScope.launch(Dispatchers.Main) {
                        strategyRocket.stopStrategyCommand()
                    }
                } else if (ticker == "TREND") {
                    GlobalScope.launch(Dispatchers.Main) {
                        strategyTrend.stopStrategyCommand()
                    }
                } else if (ticker == "LIMIT") {
                    GlobalScope.launch(Dispatchers.Main) {
                        strategyLimits.stopStrategyCommand()
                    }
                } else if (ticker == "ARB") {
                    GlobalScope.launch(Dispatchers.Main) {
                        strategyArbitration.stopStrategyCommand()
                    }
                } else if (ticker == "2358") {
                    GlobalScope.launch(Dispatchers.Main) {
                        strategy2358.stopStrategyCommand()
                    }
                } else if (ticker == "2358DL") {
                    GlobalScope.launch(Dispatchers.Main) {
                        strategyDayLow.stopStrategyCommand()
                    }
                } else if (ticker == "2225") {
                    GlobalScope.launch(Dispatchers.Main) {
                        strategy2225.stopStrategyCommand()
                    }
                }
                return 2
            } else if (operation == "start") {
                if (ticker == "2358") {
                    GlobalScope.launch(Dispatchers.Main) {
                        val tickers = mutableListOf<String>()
                        for (i in 2 until list.size) {
                            tickers.add(list[i])
                        }
                        strategy2358.prepareStrategyCommand(tickers)
                    }
                    return 2
                } else if (ticker == "2358DL") {
                    GlobalScope.launch(Dispatchers.Main) {
                        val tickers = mutableListOf<String>()
                        for (i in 2 until list.size) {
                            tickers.add(list[i])
                        }
                        strategyDayLow.prepareStrategyCommand(tickers)
                    }
                    return 2
                } else if (ticker == "2225") {
                    GlobalScope.launch(Dispatchers.Main) {
                        val tickers = mutableListOf<String>()
                        for (i in 2 until list.size) {
                            tickers.add(list[i])
                        }
                        strategy2225.prepareStrategyCommand(tickers)
                    }
                    return 2
                }
            }


            val stock = stockManager.getStockByTicker(ticker)
            val figi = stock?.figi ?: ""
            if (figi == "" || stock == null) return 0

            if (SettingsManager.getTelegramAllowCommandBuySell()) {
                if (operation in listOf("buy", "sell")) {
                    if (list.size != 5) return 0
                    val price = list[3].toDouble()
                    val percent = list[4].toInt()
                    if (price == 0.0 || percent == 0) return 0

                    if (operation == "buy") {   // ! buy vips 29.46 1
                        val moneyPart = SettingsManager.getFollowerPurchaseVolume() / 100.0 * percent
                        val lots = (moneyPart / price).toInt()

                        // недостаточно для покупки даже одного лота
                        if (lots == 0 || moneyPart == 0.0) return 0

                        // превышен лимит торговли по сигналам
                        if (abs(moneySpent - lots * price) > SettingsManager.getFollowerPurchaseVolume()) return 0

                        GlobalScope.launch(Dispatchers.Main) {
                            try {
                                ordersService.placeLimitOrder(
                                    lots,
                                    figi,
                                    price,
                                    OperationType.BUY,
                                    portfolioManager.getActiveBrokerAccountId()
                                )
                                Utils.showToastAlert("$ticker новый ордер: ПОКУПКА!")
                            } catch (e: Exception) {

                            }
                        }
                        moneySpent -= lots * price
                    }

                    if (operation == "sell") {  // # SELL VIPS 29.46 1
                        val position = portfolioManager.getPositionForStock(stock) ?: return 0
                        val lots = (position.getLots() / 100.0 * percent).toInt()

                        if (lots == 0) return 0

                        // сильно большая поза, не сдавать
//                    if (lots * price > SettingsManager.getFollowerPurchaseVolume() || position.lots * price > SettingsManager.getFollowerPurchaseVolume()) return 0

                        GlobalScope.launch(Dispatchers.Main) {
                            try {
                                ordersService.placeLimitOrder(
                                    lots,
                                    figi,
                                    price,
                                    OperationType.SELL,
                                    portfolioManager.getActiveBrokerAccountId()
                                )
                                Utils.showToastAlert("$ticker новый ордер: ПРОДАЖА!")
                            } catch (e: Exception) {

                            }
                        }
                        moneySpent += lots * price
                    }
                }

                if (operation in listOf("buy_move", "sell_move")) { // # BUY_MOVE VIPS 0.01
                    if (list.size != 4) return 0

                    GlobalScope.launch(Dispatchers.Main) {
                        val change = list[3].toDouble()
                        val operationType = if ("sell" in operation) OperationType.SELL else OperationType.BUY
                        val orders = portfolioManager.getOrderAllForStock(stock, operationType)
                        orders.forEach { order ->
                            val newIntPrice = ((order.price + change) * 100).roundToInt()
                            val newPrice: Double = Utils.makeNicePrice(newIntPrice / 100.0, order.stock)
                            brokerManager.replaceOrderTinkoff(order, newPrice, operationType)
                        }
                    }
                }

                if (operation in listOf("buy_cancel", "sell_cancel")) { // # BUY_CANCEL VIPS
                    if (list.size != 3) return 0

                    GlobalScope.launch(Dispatchers.Main) {
                        val operationType = if ("sell" in operation) OperationType.SELL else OperationType.BUY
                        val orders = portfolioManager.getOrderAllForStock(stock, operationType)
                        orders.forEach { order ->
                            brokerManager.cancelOrderTinkoff(order)

                            val money = (order.requestedLots - order.executedLots) * order.price
                            if (operationType == OperationType.SELL) {
                                moneySpent += money
                            } else {
                                moneySpent -= money
                            }
                        }
                    }
                }
            }

            return 1
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return 0
    }

    fun startStrategy() {
        started = true

        jobRefreshDeposit?.cancel()
        jobRefreshDeposit = GlobalScope.launch(Dispatchers.Main) {
            while (true) {
                delay(5000)
                portfolioManager.refreshDeposit()
                portfolioManager.refreshOrders()
            }
        }
    }

    fun stopStrategy() {
        started = false
        jobRefreshDeposit?.cancel()
    }
}