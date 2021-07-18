package com.project.ti2358.data.manager

import com.project.ti2358.data.common.BaseOrder
import com.project.ti2358.data.common.BasePosition
import com.project.ti2358.data.common.BrokerType
import com.project.ti2358.data.tinkoff.model.*
import com.project.ti2358.data.tinkoff.service.MarketService
import com.project.ti2358.service.PurchaseStatus
import com.project.ti2358.service.Utils
import kotlinx.coroutines.*
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.inject
import java.util.*
import kotlin.math.abs
import kotlin.math.ceil

@KoinApiExtension
data class StockPurchaseTinkoff(override var stock: Stock, override var broker: BrokerType = BrokerType.TINKOFF) : StockPurchase(stock, broker) {
    override var ticker: String = stock.ticker
    override var figi: String = stock.figi

    private val brokerManager: BrokerManager by inject()
    private val portfolioManager: PortfolioManager by inject()
    private val marketService: MarketService by inject()
    private val strategyTrailingStop: StrategyTrailingStop by inject()

    var buyLimitOrder: BaseOrder? = null
    var sellLimitOrder: BaseOrder? = null

    override fun buyLimitFromBid(price: Double, profit: Double, counter: Int, orderLifeTimeSeconds: Int): Job? {
        if (lots > 999999999 || lots == 0 || price == 0.0) return null
        val buyPrice = Utils.makeNicePrice(price, stock)

        var profitPrice = buyPrice + buyPrice / 100.0 * profit
        profitPrice = Utils.makeNicePrice(profitPrice, stock)

        val p = portfolioManager.getPositionForStock(stock)

        val lotsPortfolio = p?.getLots() ?: 0
        var lotsToBuy = lots

        status = PurchaseStatus.WAITING
        return GlobalScope.launch(Dispatchers.Main) {
            try {
                val ticker = stock.ticker

                // счётчик на количество повторов (возможно просто нет депо) = примерно 1 минуту
                var tries = counter
                while (tries >= 0) { // выставить ордер на покупку
                    status = PurchaseStatus.ORDER_BUY_PREPARE
                    buyLimitOrder = brokerManager.placeOrderTinkoff(stock, buyPrice, lotsToBuy, OperationType.BUY)
                    delay(DelayFast)

                    if (buyLimitOrder?.isCreated() == true) {
                        status = PurchaseStatus.ORDER_BUY
                        break
                    }

                    portfolioManager.refreshOrders()
                    portfolioManager.refreshDeposit()

                    delay(DelaySuperFast)
                    tries--
                }
                if (tries < 0) { // заявка не выставилась, сворачиваем лавочку, можно вернуть один таз
                    Utils.showToastAlert("$ticker: не смогли выставить ордер на покупку по $buyPrice")
                    status = PurchaseStatus.CANCELED
                    return@launch
                }

                Utils.showToastAlert("$ticker: ордер на покупку по $buyPrice")

                if (profit == 0.0) {
                    delay(orderLifeTimeSeconds * 1000L)
                    status = PurchaseStatus.CANCELED
                    brokerManager.cancelOrder(buyLimitOrder)
                } else {
                    // проверяем появился ли в портфеле тикер
                    var position: BasePosition?
                    var iterations = 0

                    while (true) {
                        iterations++
                        try {
                            portfolioManager.refreshDeposit()
                            portfolioManager.refreshOrders()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            delay(DelayLong)
                            continue
                        }

                        if (iterations * DelayLong / 1000.0 > orderLifeTimeSeconds) { // отменить заявку на покупку
                            status = PurchaseStatus.CANCELED
                            brokerManager.cancelOrder(buyLimitOrder)
                            Utils.showToastAlert("$ticker: заявка отменена по $buyPrice")
                            return@launch
                        }

                        val orderBuy = brokerManager.getOrderForId(buyLimitOrder?.getOrderID() ?: "", OperationType.BUY)
                        position = portfolioManager.getPositionForStock(stock)

                        // проверка на большое количество лотов
                        val orders = portfolioManager.getOrderAllForStock(stock, OperationType.SELL)
                        var totalSellingLots = 0
                        orders.forEach { totalSellingLots += it.requestedLots }
                        if (totalSellingLots >= lots) break

                        // заявка стоит, ничего не куплено
                        if (orderBuy != null && position == null) {
                            status = PurchaseStatus.ORDER_BUY
                            delay(DelayLong)
                            continue
                        }

                        if (orderBuy == null && position == null) { // заявка отменена, ничего не куплено
                            status = PurchaseStatus.CANCELED
                            Utils.showToastAlert("$ticker: отмена по $buyPrice")
                            return@launch
                        }

                        position?.let { // появилась позиция, проверить есть ли что продать
                            // выставить ордер на продажу
                            val lotsToSell = it.getLots() - it.getBlocked().toInt() - lotsPortfolio
                            if (lotsToSell <= 0) {  // если свободных лотов нет, продолжаем
                                return@let
                            }

                            lotsToBuy -= lotsToSell
                            if (lotsToBuy < 0) {    // если вся купленная позиция распродана, продолжаем
                                return@let
                            }

                            Utils.showToastAlert("$ticker: куплено по $buyPrice")

                            sellLimitOrder = brokerManager.placeOrderTinkoff(stock, profitPrice, lotsToSell, OperationType.SELL)

                            if (sellLimitOrder?.isCreated() == true) {
                                status = PurchaseStatus.ORDER_SELL
                                Utils.showToastAlert("$ticker: ордер на продажу по $profitPrice")
                            } else { // заявка отклонена, вернуть лоты
                                lotsToBuy += lotsToSell
                            }
                        }

                        if (orderBuy == null) { // если ордер исчез - удалён вручную или весь заполнился - завершаем
                            status = PurchaseStatus.ORDER_SELL
                            break
                        }

                        delay(DelayLong)
                    }
                }

                if (status == PurchaseStatus.ORDER_SELL) {
                    while (true) {
                        delay(DelayLong)
                        val position = portfolioManager.getPositionForStock(stock)
                        if (position == null || position.getLots() == lotsPortfolio) { // продано!
                            status = PurchaseStatus.SOLD
                            Utils.showToastAlert("$ticker: продано!?")
                            break
                        }
                    }
                }

            } catch (e: Exception) {
                status = PurchaseStatus.CANCELED
                e.printStackTrace()
            }
        }
    }

    override fun sellLimitFromAsk(price: Double, profit: Double, counter: Int, orderLifeTimeSeconds: Int): Job? {
        if (lots > 999999999 || lots == 0 || price == 0.0) return null
        val sellPrice = Utils.makeNicePrice(price, stock)

        var profitPrice = sellPrice - sellPrice / 100.0 * profit
        profitPrice = Utils.makeNicePrice(profitPrice, stock)

        val p = portfolioManager.getPositionForStock(stock)

        val lotsPortfolio = abs(p?.getLots() ?: 0)
        var lotsToSell = lots

        status = PurchaseStatus.WAITING
        return GlobalScope.launch(Dispatchers.Main) {
            try {
                val figi = stock.figi
                val ticker = stock.ticker

                // счётчик на количество повторов (возможно просто нет депо) = примерно 1 минуту
                var tries = counter
                while (tries >= 0) { // выставить ордер на покупку
                    status = PurchaseStatus.ORDER_BUY_PREPARE
                    sellLimitOrder = brokerManager.placeOrderTinkoff(stock, sellPrice, lotsToSell, OperationType.SELL)
                    delay(DelayFast)

                    if (sellLimitOrder?.isCreated() == true) {
                        status = PurchaseStatus.ORDER_SELL
                        break
                    }

                    portfolioManager.refreshOrders()
                    portfolioManager.refreshDeposit()

                    delay(DelaySuperFast)
                    tries--
                }
                if (tries < 0) { // заявка не выставилась, сворачиваем лавочку, можно вернуть один таз
                    Utils.showToastAlert("$ticker: не смогли выставить ордер на шорт по $sellPrice")
                    status = PurchaseStatus.CANCELED
                    return@launch
                }

                Utils.showToastAlert("$ticker: ордер на шорт по $sellPrice")

                if (profit == 0.0) {
                    delay(orderLifeTimeSeconds * 1000L)
                    status = PurchaseStatus.CANCELED
                    brokerManager.cancelOrder(sellLimitOrder)
                } else {
                    // проверяем появился ли в портфеле тикер
                    var position: BasePosition?
                    var iterations = 0

                    while (true) {
                        iterations++
                        try {
                            portfolioManager.refreshDeposit()
                            portfolioManager.refreshOrders()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            delay(DelayLong)
                            continue
                        }

                        if (iterations * DelayLong / 1000.0 > orderLifeTimeSeconds) { // отменить заявку на покупку
                            status = PurchaseStatus.CANCELED
                            brokerManager.cancelOrder(sellLimitOrder)
                            Utils.showToastAlert("$ticker: заявка отменена по $sellPrice")
                            return@launch
                        }

                        val orderSell = brokerManager.getOrderForId(sellLimitOrder?.getOrderID() ?: "", OperationType.SELL)
                        position = portfolioManager.getPositionForStock(stock)

                        // проверка на большое количество лотов
                        val orders = portfolioManager.getOrderAllForStock(stock, OperationType.BUY)
                        var totalBuyingLots = 0
                        orders.forEach { totalBuyingLots += it.requestedLots }
                        if (totalBuyingLots >= lots) break

                        // заявка стоит, ничего не куплено
                        if (orderSell != null && position == null) {
                            status = PurchaseStatus.ORDER_SELL
                            delay(DelayLong)
                            continue
                        }

                        if (orderSell == null && position == null) { // заявка отменена, ничего не куплено
                            status = PurchaseStatus.CANCELED
                            Utils.showToastAlert("$ticker: не налили по $sellPrice")
                            return@launch
                        }

                        position?.let { // появилась позиция, проверить есть ли что продать
                            // выставить ордер на продажу
                            val lotsToBuy = abs(it.getLots()) - abs(it.blocked.toInt()) - lotsPortfolio
                            if (lotsToBuy <= 0) {  // если свободных лотов нет, продолжаем
                                return@let
                            }

                            lotsToSell -= lotsToBuy
                            if (lotsToSell < 0) {    // если вся проданная позиция выкуплена, выходим
                                return@let
                            }

                            Utils.showToastAlert("$ticker: продано по $sellPrice")

                            buyLimitOrder = brokerManager.placeOrderTinkoff(stock, profitPrice, lotsToBuy, OperationType.BUY)

                            if (buyLimitOrder?.isCreated() == true) {
                                status = PurchaseStatus.ORDER_BUY
                                Utils.showToastAlert("$ticker: ордер на откуп шорта по $profitPrice")
                            } else { // заявка отклонена, вернуть лоты
                                lotsToSell += lotsToBuy
                            }
                        }

                        if (orderSell == null) { // если ордер исчез - удалён вручную или весь заполнился - завершаем
                            status = PurchaseStatus.ORDER_BUY
                            break
                        }

                        delay(DelayLong)
                    }
                }

                if (status == PurchaseStatus.ORDER_BUY) {
                    while (true) {
                        delay(DelayLong)
                        val position = portfolioManager.getPositionForStock(stock)
                        if (position == null || position.getLots() == lotsPortfolio) { // продано!
                            status = PurchaseStatus.SOLD
                            Utils.showToastAlert("$ticker: продано!?")
                            break
                        }
                    }
                }

            } catch (e: Exception) {
                status = PurchaseStatus.CANCELED
                e.printStackTrace()
            }
        }
    }

    override fun buyFromAsk1728(): Job? {
        if (lots == 0) return null

        return GlobalScope.launch(StockManager.stockContext) {
            try {
                val ticker = stock.ticker
                val figi = stock.figi

                status = PurchaseStatus.ORDER_BUY_PREPARE

                val orderbook = marketService.orderbook(figi, 10)
                val buyPrice = orderbook.getBestPriceFromAsk(lots)
                if (buyPrice == 0.0) return@launch

                buyLimitOrder = brokerManager.placeOrderTinkoff(stock, buyPrice, lots, OperationType.BUY)
                if (buyLimitOrder?.isCreated() == true) {
                    status = PurchaseStatus.ORDER_BUY
                } else {
                    Utils.showToastAlert("$ticker: недостаточно средств $buyPrice")
                    return@launch
                }

                Utils.showToastAlert("$ticker: ордер на покупку по $buyPrice")

                delay(DelayFast)

                // проверяем появился ли в портфеле тикер
                var position: BasePosition?
                while (true) {
                    try {
                        portfolioManager.refreshDeposit()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    position = portfolioManager.getPositionForStock(stock)
                    if (position != null && position.getLots() >= lots) {
                        status = PurchaseStatus.BOUGHT
                        Utils.showToastAlert("$ticker: куплено!")
                        break
                    }

                    delay(DelayLong)
                }

                if (trailingStop) { // запускаем трейлинг стоп
                    currentTrailingStop = TrailingStop(stock, buyPrice, trailingStopTakeProfitPercentActivation, trailingStopTakeProfitPercentDelta, trailingStopStopLossPercent)
                    currentTrailingStop?.let {
                        strategyTrailingStop.addTrailingStop(it)
                        status = PurchaseStatus.ORDER_SELL_TRAILING

                        // вся логика ТС тут, очень долгий процесс
                        var profitSellPrice = it.process()
                        strategyTrailingStop.removeTrailingStop(it)

                        status = PurchaseStatus.ORDER_SELL_PREPARE
                        if (profitSellPrice == 0.0) return@launch

                        // выставить ордер на продажу в лучший бид
                        if (SettingsManager.getTrailingStopSellBestBid()) {
                            val orderbook = marketService.orderbook(figi, 5)
                            profitSellPrice = orderbook.getBestPriceFromBid(lots)
                        }

                        if (profitSellPrice == 0.0) return@launch

                        while (true) {
                            profitSellPrice = Utils.makeNicePrice(profitSellPrice, stock)
                            sellLimitOrder = brokerManager.placeOrderTinkoff(stock, profitSellPrice, lots, OperationType.SELL)
                            if (sellLimitOrder?.isCreated() == true) {
                                status = PurchaseStatus.ORDER_SELL
                                break
                            }
                            delay(DelayMiddle)
                        }
                    }
                } else { // лимитка на продажу
                    position?.let {
                        val profit = SettingsManager.get1728TakeProfit()
                        if (profit == 0.0 || buyPrice == 0.0) return@launch
                        var profitPrice = buyPrice + buyPrice / 100.0 * profit
                        profitPrice = Utils.makeNicePrice(profitPrice, stock)

                        // выставить ордер на продажу
                        while (true) {
                            status = PurchaseStatus.ORDER_SELL_PREPARE
                            sellLimitOrder = brokerManager.placeOrderTinkoff(stock, profitPrice, lots, OperationType.SELL)
                            if (sellLimitOrder?.isCreated() == true) {
                                status = PurchaseStatus.ORDER_SELL
                                break
                            }
                            delay(DelayMiddle)
                        }

                        Utils.showToastAlert("$ticker: ордер на продажу по $profitPrice")
                    }
                }

                while (true) {
                    position = portfolioManager.getPositionForStock(stock)
                    if (position == null) { // продано!
                        status = PurchaseStatus.SOLD
                        Utils.showToastAlert("$ticker: продано!")
                        break
                    }
                    delay(DelayLong)
                }
            } catch (e: Exception) {
                status = PurchaseStatus.CANCELED
                currentTrailingStop?.let {
                    strategyTrailingStop.removeTrailingStop(it)
                }
                e.printStackTrace()
            }
        }
    }

    override fun buyFromAsk2358(): Job? {
        if (lots == 0) return null

        return GlobalScope.launch(StockManager.stockContext) {
            try {
                val ticker = stock.ticker
                val figi = stock.figi

                var buyPrice: Double = 0.0
                status = PurchaseStatus.ORDER_BUY_PREPARE
                val orderbook = marketService.orderbook(figi, 10)
                buyPrice = orderbook.getBestPriceFromAsk(lots)
                if (buyPrice == 0.0) return@launch

                buyLimitOrder = brokerManager.placeOrderTinkoff(stock, buyPrice, lots, OperationType.BUY)
                if (buyLimitOrder?.isCreated() == true) {
                    status = PurchaseStatus.ORDER_BUY
                } else {
                    status = PurchaseStatus.CANCELED
                    Utils.showToastAlert("$ticker: недостаточно средств для покупки $buyPrice")
                    return@launch
                }

                Utils.showToastAlert("$ticker: ордер на покупку по $buyPrice")
                delay(DelayFast)

                // проверяем появился ли в портфеле тикер
                var position: BasePosition?
                while (true) {
                    try {
                        portfolioManager.refreshDeposit()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    position = portfolioManager.getPositionForStock(stock)
                    if (position != null && position.getLots() >= lots) {
                        status = PurchaseStatus.BOUGHT
                        Utils.showToastAlert("$ticker: куплено!")
                        break
                    }

                    delay(DelayLong)
                }

                if (trailingStop) { // запускаем трейлинг стоп
                    currentTrailingStop = TrailingStop(stock, buyPrice, trailingStopTakeProfitPercentActivation, trailingStopTakeProfitPercentDelta, trailingStopStopLossPercent)
                    currentTrailingStop?.let {
                        strategyTrailingStop.addTrailingStop(it)
                        status = PurchaseStatus.ORDER_SELL_TRAILING

                        // вся логика ТС тут, очень долгий процесс
                        var profitSellPrice = it.process()
                        strategyTrailingStop.removeTrailingStop(it)

                        status = PurchaseStatus.ORDER_SELL_PREPARE
                        if (profitSellPrice == 0.0) return@launch

                        if (SettingsManager.getTrailingStopSellBestBid()) { // выставить ордер на продажу в лучший бид
                            val localOrderbook = marketService.orderbook(figi, 5)
                            profitSellPrice = localOrderbook.getBestPriceFromBid(lots)
                        }
                        if (profitSellPrice == 0.0) return@launch

                        profitSellPrice = Utils.makeNicePrice(profitSellPrice, stock)
                        sellLimitOrder = brokerManager.placeOrderTinkoff(stock, profitSellPrice, lots, OperationType.SELL)
                        if (sellLimitOrder?.isCreated() == true) {
                            status = PurchaseStatus.ORDER_SELL
                        } else {
                            status = PurchaseStatus.ERROR_NEED_WATCH
                        }
                    }
                } else { // продажа 2358 лесенкой
                    position?.let {
                        val totalLots = lots
                        val profitFrom = if (percentProfitSellFrom != 0.0) percentProfitSellFrom else SettingsManager.get2358TakeProfitFrom()
                        val profitTo = if (percentProfitSellTo != 0.0) percentProfitSellTo else SettingsManager.get2358TakeProfitTo()

                        val profitStep = SettingsManager.get2358TakeProfitStep()

                        // в случае кривых настроек просто не создаём заявки
                        if (profitTo < profitFrom || profitStep == 0 || profitFrom == 0.0 || profitTo == 0.0) return@launch

                        val list: MutableList<Pair<Int, Double>> = mutableListOf()
                        when (profitStep) {
                            1 -> { // если шаг 1, то создать заявку на нижний % и всё
                                list.add(Pair(totalLots, profitFrom))
                            }
                            2 -> { // первый и последний
                                val partLots1 = totalLots / 2
                                val partLots2 = totalLots - partLots1
                                list.add(Pair(partLots1, profitFrom))
                                list.add(Pair(partLots2, profitTo))
                            }
                            else -> { // промежуточные
                                val profitStepDouble: Double = profitStep.toDouble()
                                val delta = (profitTo - profitFrom) / (profitStep - 1)

                                // округляем в бОльшую, чтобы напоследок осталось мало лотов
                                val basePartLots: Int = ceil(totalLots / profitStepDouble).toInt()

                                var currentLots = basePartLots
                                var currentProfit = profitFrom

                                // стартовый профит
                                list.add(Pair(basePartLots, currentProfit))

                                var step = profitStep - 2
                                while (step > 0) {
                                    if (currentLots + basePartLots > totalLots) {
                                        break
                                    }
                                    currentLots += basePartLots
                                    currentProfit += delta
                                    list.add(Pair(basePartLots, currentProfit))
                                    step--
                                }

                                // финальный профит
                                val lastPartLots = totalLots - currentLots
                                if (lastPartLots > 0) {
                                    list.add(Pair(lastPartLots, profitTo))
                                }
                            }
                        }

                        if (list.isEmpty()) return@launch

                        status = PurchaseStatus.ORDER_SELL_PREPARE
                        for (p in list) {
                            val lotsStep = p.first
                            val profit = p.second

                            // вычисляем и округляем до 2 после запятой
                            var profitPrice = buyPrice + buyPrice / 100.0 * profit
                            profitPrice = Utils.makeNicePrice(profitPrice, stock)

                            if (lotsStep <= 0 || profitPrice == 0.0) continue

                            // выставить ордер на продажу
                            sellLimitOrder = brokerManager.placeOrderTinkoff(stock, profitPrice, lotsStep, OperationType.SELL)
                            if (sellLimitOrder?.isCreated() == true) {
                                status = PurchaseStatus.ORDER_SELL
                            } else {
                                status = PurchaseStatus.ERROR_NEED_WATCH
                            }
                            delay(DelayFast)
                        }
                        Utils.showToastAlert("$ticker: ордера на продажу от $percentProfitSellFrom% до $percentProfitSellTo%")
                    }
                }

                while (true) {
                    delay(DelayLong * 5)
                    if (portfolioManager.getPositionForStock(stock) == null) { // продано!
                        status = PurchaseStatus.SOLD
                        Utils.showToastAlert("$ticker: продано!")
                        break
                    }
                }
            } catch (e: Exception) {
                status = PurchaseStatus.CANCELED
                currentTrailingStop?.let {
                    strategyTrailingStop.removeTrailingStop(it)
                }
                e.printStackTrace()
            }
        }
    }

    override fun sellWithLimit(): Job? {
        val pos = portfolioManager.getPositionForStock(stock)
        if (pos == null || lots == 0 || percentProfitSellFrom == 0.0) {
            status = PurchaseStatus.CANCELED
            return null
        }

        return GlobalScope.launch(Dispatchers.Main) {
            position = pos

            val profitPrice = getProfitPriceForSell()
            if (profitPrice == 0.0) return@launch

            status = PurchaseStatus.ORDER_SELL_PREPARE
            sellLimitOrder = brokerManager.placeOrderTinkoff(stock, profitPrice, lots, OperationType.SELL)
            if (sellLimitOrder?.isCreated() == true) {
                status = PurchaseStatus.ORDER_SELL
            } else {
                status = PurchaseStatus.CANCELED
            }
        }
    }

    override fun sellToBestBid(): Job? {
        val pos = portfolioManager.getPositionForStock(stock)
        if (pos == null || pos.getLots() == 0) {
            status = PurchaseStatus.CANCELED
            return null
        }

        return GlobalScope.launch(Dispatchers.Main) {
            position = pos
            status = PurchaseStatus.ORDER_SELL_PREPARE

            val orderbook = marketService.orderbook(stock.figi, 5)
            val bestBid = orderbook.getBestPriceFromBid(lots)
            val profitSellPrice = Utils.makeNicePrice(bestBid, stock)

            sellLimitOrder = brokerManager.placeOrderTinkoff(stock, profitSellPrice, lots, OperationType.SELL)
            if (sellLimitOrder?.isCreated() == true) {
                status = PurchaseStatus.ORDER_SELL
            } else {
                status = PurchaseStatus.CANCELED
            }
        }
    }

    override fun sellToBestAsk(): Job? {
        val figi = stock.figi
        val pos = portfolioManager.getPositionForStock(stock)
        if (pos == null || pos.getLots() == 0) {
            status = PurchaseStatus.CANCELED
            return null
        }

        return GlobalScope.launch(Dispatchers.Main) {
            position = pos
            status = PurchaseStatus.ORDER_SELL_PREPARE

            val orderbook = marketService.orderbook(figi, 10)
            val bestAsk = orderbook.getBestPriceFromAsk(lots)
            val profitSellPrice = Utils.makeNicePrice(bestAsk, stock)

            sellLimitOrder = brokerManager.placeOrderTinkoff(stock, profitSellPrice, lots, OperationType.SELL)
            if (sellLimitOrder?.isCreated() == true) {
                status = PurchaseStatus.ORDER_SELL
            } else {
                status = PurchaseStatus.CANCELED
            }
        }
    }

    override fun sellWithTrailing(): Job? {
        val figi = stock.figi

        val pos = portfolioManager.getPositionForStock(stock)
        if (pos == null || pos.getLots() == 0 || percentProfitSellFrom == 0.0) {
            status = PurchaseStatus.CANCELED
            return null
        }

        return GlobalScope.launch(StockManager.stockContext) {
            position = pos
            currentTrailingStop = TrailingStop(stock, stock.getPriceNow(), trailingStopTakeProfitPercentActivation, trailingStopTakeProfitPercentDelta, trailingStopStopLossPercent)
            currentTrailingStop?.let {
                strategyTrailingStop.addTrailingStop(it)
                status = PurchaseStatus.ORDER_SELL_TRAILING

                // вся логика ТС тут, очень долгий процесс
                var profitSellPrice = it.process()
                strategyTrailingStop.removeTrailingStop(it)

                status = PurchaseStatus.ORDER_SELL_PREPARE
                if (profitSellPrice == 0.0) return@launch

                profitSellPrice = Utils.makeNicePrice(profitSellPrice, stock)
                sellLimitOrder = brokerManager.placeOrderTinkoff(stock, profitSellPrice, lots, OperationType.SELL)
                if (sellLimitOrder?.isCreated() == true) {
                    status = PurchaseStatus.ORDER_SELL
                } else {
                    status = PurchaseStatus.CANCELED
                }
            }
        }
    }

    override fun sellShortToBid2225(): Job? {
        if (lots == 0) return null

        return GlobalScope.launch(Dispatchers.Main) {
            try {
                val ticker = stock.ticker
                val figi = stock.figi

                status = PurchaseStatus.ORDER_SELL_PREPARE
                val orderbook = marketService.orderbook(figi, 10)
                val sellPrice = orderbook.getBestPriceFromBid(lots)
                if (sellPrice == 0.0) return@launch

                sellLimitOrder = brokerManager.placeOrderTinkoff(stock, sellPrice, lots, OperationType.SELL)
                if (sellLimitOrder?.isCreated() == true) {
                    status = PurchaseStatus.ORDER_SELL
                } else {
                    status = PurchaseStatus.CANCELED
                    Utils.showToastAlert("$ticker: недостаточно средств для шорта $sellPrice")
                    return@launch
                }

                Utils.showToastAlert("$ticker: ордер на продажу по $sellPrice")
                delay(DelayFast)

                // проверяем появился ли в портфеле тикер
                var position: BasePosition?
                while (true) {
                    try {
                        portfolioManager.refreshDeposit()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    position = portfolioManager.getPositionForStock(stock)
                    if (position != null && abs(position.getLots()) >= lots) {
                        status = PurchaseStatus.BOUGHT
                        Utils.showToastAlert("$ticker: продано!")
                        break
                    }

                    delay(DelayLong)
                }

//                if (trailingStop) { // запускаем трейлинг стоп
//                    currentTrailingStop = TrailingStop(stock, sellPrice, trailingStopTakeProfitPercentActivation, trailingStopTakeProfitPercentDelta, trailingStopStopLossPercent)
//                    currentTrailingStop?.let {
//                        strategyTrailingStop.addTrailingStop(it)
//                        status = PurchaseStatus.ORDER_SELL_TRAILING
//
//                        // вся логика ТС тут, очень долгий процесс
//                        var profitSellPrice = it.process()
//                        strategyTrailingStop.removeTrailingStop(it)
//
//                        status = PurchaseStatus.ORDER_SELL_PREPARE
//                        if (profitSellPrice == 0.0) return@launch
//
//                        if (SettingsManager.getTrailingStopSellBestBid()) { // выставить ордер на продажу в лучший бид
//                            val orderbook = marketService.orderbook(figi, 5)
//                            profitSellPrice = orderbook.getBestPriceFromBid(lots)
//                        }
//                        if (profitSellPrice == 0.0) return@launch
//
//                        try {
//                            profitSellPrice = Utils.makeNicePrice(profitSellPrice)
//                            sellLimitOrder = ordersService.placeLimitOrder(
//                                lots,
//                                figi,
//                                profitSellPrice,
//                                OperationType.SELL,
//                                depositManager.getActiveBrokerAccountId()
//                            )
//                            status = PurchaseStatus.ORDER_SELL
//                        } catch (e: Exception) {
//                            e.printStackTrace()
//                            status = PurchaseStatus.ERROR_NEED_WATCH
//                        }
//                    }
//                } else { // откуп 2225 лесенкой
                    position?.let {
                        val totalLots = abs(it.getLots())
                        val profitFrom = if (percentProfitSellFrom != 0.0) percentProfitSellFrom else SettingsManager.get2225TakeProfitFrom()
                        val profitTo = if (percentProfitSellTo != 0.0) percentProfitSellTo else SettingsManager.get2225TakeProfitTo()

                        val profitStep = SettingsManager.get2225TakeProfitStep()

                        // в случае кривых настроек просто не создаём заявки
                        if (profitTo < profitFrom || profitStep == 0 || profitFrom == 0.0 || profitTo == 0.0) return@launch

                        val list: MutableList<Pair<Int, Double>> = mutableListOf()
                        when (profitStep) {
                            1 -> { // если шаг 1, то создать заявку на нижний % и всё
                                list.add(Pair(totalLots, profitFrom))
                            }
                            2 -> { // первый и последний
                                val partLots1 = totalLots / 2
                                val partLots2 = totalLots - partLots1
                                list.add(Pair(partLots1, profitFrom))
                                list.add(Pair(partLots2, profitTo))
                            }
                            else -> { // промежуточные
                                val profitStepDouble: Double = profitStep.toDouble()
                                val delta = (profitTo - profitFrom) / (profitStep - 1)

                                // округляем в бОльшую, чтобы напоследок осталось мало лотов
                                val basePartLots: Int = ceil(totalLots / profitStepDouble).toInt()

                                var currentLots = basePartLots
                                var currentProfit = profitFrom

                                // стартовый профит
                                list.add(Pair(basePartLots, currentProfit))

                                var step = profitStep - 2
                                while (step > 0) {
                                    if (currentLots + basePartLots > totalLots) {
                                        break
                                    }
                                    currentLots += basePartLots
                                    currentProfit += delta
                                    list.add(Pair(basePartLots, currentProfit))
                                    step--
                                }

                                // финальный профит
                                val lastPartLots = totalLots - currentLots
                                if (lastPartLots > 0) {
                                    list.add(Pair(lastPartLots, profitTo))
                                }
                            }
                        }

                        if (list.isEmpty()) return@launch

                        status = PurchaseStatus.ORDER_BUY_PREPARE
                        for (p in list) {
                            val lotsStep = p.first
                            val profit = p.second

                            // вычисляем и округляем до 2 после запятой
                            var profitPrice = sellPrice - sellPrice / 100.0 * profit
                            profitPrice = Utils.makeNicePrice(profitPrice, stock)

                            if (lotsStep <= 0 || profitPrice == 0.0) continue

                            // выставить ордер на откуп
                            buyLimitOrder = brokerManager.placeOrderTinkoff(stock, profitPrice, lotsStep, OperationType.BUY)
                            if (buyLimitOrder?.isCreated() == true) {
                                status = PurchaseStatus.ORDER_BUY
                            } else {
                                status = PurchaseStatus.ERROR_NEED_WATCH
                            }
                            delay(DelayFast)
                        }
                        Utils.showToastAlert("$ticker: ордера на откуп от $percentProfitSellFrom% до $percentProfitSellTo%")
                    }
//                }

                while (true) {
                    delay(DelayLong * 5)
                    if (portfolioManager.getPositionForStock(stock) == null) { // продано!
                        status = PurchaseStatus.SOLD
                        Utils.showToastAlert("$ticker: зашорчено!")
                        break
                    }
                }
            } catch (e: Exception) {
                status = PurchaseStatus.CANCELED
                currentTrailingStop?.let {
                    strategyTrailingStop.removeTrailingStop(it)
                }
                e.printStackTrace()
            }
        }
    }
}
