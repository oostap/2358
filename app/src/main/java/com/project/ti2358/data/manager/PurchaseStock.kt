package com.project.ti2358.data.manager

import com.project.ti2358.data.model.dto.*
import com.project.ti2358.data.service.*
import com.project.ti2358.service.Utils
import com.project.ti2358.service.log
import kotlinx.coroutines.*
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*
import kotlin.math.abs
import kotlin.math.ceil

enum class PurchaseStatus {
    NONE,
    ORDER_BUY_PREPARE,
    ORDER_BUY,
    BOUGHT,
    ORDER_SELL_TRAILING,
    ORDER_SELL_PREPARE,
    ORDER_SELL,
    WAITING,
    SOLD,
    CANCELED,
    NOT_FILLED,
    PART_FILLED,

    ERROR_NEED_WATCH,
}

@KoinApiExtension
data class PurchaseStock(
    var stock: Stock
) : KoinComponent {
    private val ordersService: OrdersService by inject()
    private val depositManager: DepositManager by inject()
    private val marketService: MarketService by inject()
    private val strategyTrailingStop: StrategyTrailingStop by inject()

    lateinit var position: PortfolioPosition
    var fixedPrice: Double = 0.0                      // зафиксированная цена, от которой шагаем лимитками
    var percentLimitPriceChange: Double = 0.0         // разница в % с текущей ценой для создания лимитки
    var absoluteLimitPriceChange: Double = 0.0        // если лимитка, то по какой цене

    var lots: Int = 0                                 // сколько штук тарим / продаём
    var status: PurchaseStatus = PurchaseStatus.NONE

    var buyMarketOrder: MarketOrder? = null
    var buyLimitOrder: LimitOrder? = null
    var sellLimitOrder: LimitOrder? = null

    var percentProfitSellFrom: Double = 0.0
    var percentProfitSellTo: Double = 0.0

    var currentTrailingStop: TrailingStop? = null
    var trailingStop: Boolean = false
    var trailingStopTakeProfitPercentActivation: Double = 0.0
    var trailingStopTakeProfitPercentDelta: Double = 0.0
    var trailingStopStopLossPercent: Double = 0.0

    companion object {
        const val DelayFast: Long = 150
        const val DelayMiddle: Long = 400
        const val DelayLong: Long = 2000
    }

    fun getPriceString(): String {
        return "%.1f$".format(locale = Locale.US, fixedPrice * lots)
    }

    fun getStatusString(): String =
        when (status) {
            PurchaseStatus.NONE -> "NONE"
            PurchaseStatus.WAITING -> "ждём ⏳"
            PurchaseStatus.ORDER_BUY_PREPARE -> "ордер: до покупки"
            PurchaseStatus.ORDER_BUY -> "ордер: покупка!"
            PurchaseStatus.BOUGHT -> "куплено! 💸"
            PurchaseStatus.ORDER_SELL_TRAILING -> "ТТ 📈"
            PurchaseStatus.ORDER_SELL_PREPARE -> "ордер: до продажи"
            PurchaseStatus.ORDER_SELL -> "ордер: продажа!"
            PurchaseStatus.SOLD -> "продано! 🤑"
            PurchaseStatus.CANCELED -> "отменена! 🛑"
            PurchaseStatus.NOT_FILLED -> "не налили 😰"
            PurchaseStatus.PART_FILLED -> "частично налили, продаём"
            PurchaseStatus.ERROR_NEED_WATCH -> "ошибка, дальше руками 🤷‍"
        }

    fun getLimitPriceDouble(): Double {
        val buyPrice = fixedPrice + absoluteLimitPriceChange
        return Utils.makeNicePrice(buyPrice)
    }

    fun addLots(lot: Int) {
        lots += lot
        if (lots < 1) lots = 1
    }

    fun addPriceLimitPercent(change: Double) {
        percentLimitPriceChange += change
        updateAbsolutePrice()
    }

    fun updateAbsolutePrice() {
        fixedPrice = stock.getPriceDouble()
        absoluteLimitPriceChange = fixedPrice / 100 * percentLimitPriceChange
        absoluteLimitPriceChange = Utils.makeNicePrice(absoluteLimitPriceChange)
    }

    fun addPriceProfit2358Percent(change: Double) {
        percentProfitSellFrom += change
        percentProfitSellTo += change
    }

    fun addPriceProfit2358TrailingTakeProfit(change: Double) {
        trailingStopTakeProfitPercentActivation += change
        trailingStopTakeProfitPercentDelta += change * 0.4
    }

    fun buyMarket(price: Double): Job? {
        if (lots == 0) return null

        val sellPrice = Utils.makeNicePrice(price)

        return GlobalScope.launch(Dispatchers.Main) {
            try {
                while (true) {
                    try {
                        status = PurchaseStatus.ORDER_BUY_PREPARE
                        buyMarketOrder = ordersService.placeMarketOrder(
                            lots,
                            stock.figi,
                            OperationType.BUY,
                            depositManager.getActiveBrokerAccountId()
                        )
                        status = PurchaseStatus.ORDER_BUY
                        break
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    delay(DelayFast)
                }

                // проверяем появился ли в портфеле тикер
                var position: PortfolioPosition? = null
                var counter = 50
                while (counter > 0) {
                    depositManager.refreshDeposit()

                    position = depositManager.getPositionForFigi(stock.figi)
                    if (position != null && position.lots >= lots) { // куплено!
                        status = PurchaseStatus.BOUGHT
                        break
                    }

                    delay(DelayLong)
                    counter--
                }

                if (sellPrice == 0.0) return@launch

                // выставить ордер на продажу
                try {
                    status = PurchaseStatus.ORDER_SELL_PREPARE
                    position?.let {
                        sellLimitOrder = ordersService.placeLimitOrder(
                            it.lots,
                            stock.figi,
                            sellPrice,
                            OperationType.SELL,
                            depositManager.getActiveBrokerAccountId()
                        )
                    }
                    status = PurchaseStatus.ORDER_SELL
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(DelayFast)

                while (true) {
                    delay(DelayLong)

                    position = depositManager.getPositionForFigi(stock.figi)
                    if (position == null) { // продано!
                        status = PurchaseStatus.SOLD
                        break
                    }
                }

            } catch (e: Exception) {
                status = PurchaseStatus.CANCELED
                e.printStackTrace()
            }
        }
    }

    fun buyLimitFromBid(price: Double, profit: Double): Job? {
        if (lots == 0 || price == 0.0) return null
        val buyPrice = Utils.makeNicePrice(price)

        var profitPrice = buyPrice + buyPrice / 100.0 * profit
        profitPrice = Utils.makeNicePrice(profitPrice)

        var lotsToBuy = lots
        return GlobalScope.launch(Dispatchers.Main) {
            try {
                val figi = stock.figi
                val ticker = stock.ticker

                val p = depositManager.getPositionForFigi(figi)
                val initialLots = p?.lots ?: 0

                // счётчик на количество повторов (возможно просто нет депо) = примерно 1 минуту
                var counter = 50
                while (counter >= 0) { // выставить ордер на покупку
                    counter--

                    try {
                        status = PurchaseStatus.ORDER_BUY_PREPARE
                        sellLimitOrder = ordersService.placeLimitOrder(
                            lotsToBuy,
                            figi,
                            buyPrice,
                            OperationType.BUY,
                            depositManager.getActiveBrokerAccountId()
                        )
                        delay(DelayMiddle)

                        if (sellLimitOrder!!.status == OrderStatus.NEW || sellLimitOrder!!.status == OrderStatus.PENDING_NEW) {
                            status = PurchaseStatus.ORDER_BUY
                            break
                        }

                        depositManager.refreshOrders()
                        depositManager.refreshDeposit()

                        // если нет ни ордера, ни позиции, значит чета не так, повторяем
                        if (depositManager.getOrderAllOrdersForFigi(figi, OperationType.BUY).isEmpty() &&
                            depositManager.getPositionForFigi(figi) == null) continue

                        status = PurchaseStatus.ORDER_BUY
                        break
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    delay(DelayFast)
                }
                if (counter < 0) { // заявка не выставилась, сворачиваем лавочку, можно вернуть один таз
                    Utils.showToastAlert("$ticker: не смогли выставить ордер на покупку по $buyPrice")
                    return@launch
                }

                Utils.showToastAlert("$ticker: ордер на покупку по $buyPrice")

                if (profit == 0.0) return@launch

                // проверяем появился ли в портфеле тикер
                var position: PortfolioPosition?
                while (true) {
                    try {
                        depositManager.refreshDeposit()
                        depositManager.refreshOrders()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        delay(DelayLong)
                        continue
                    }

                    val orderBuy = depositManager.getOrderForFigi(figi, OperationType.BUY)
                    position = depositManager.getPositionForFigi(figi)

                    // проверка на большое количество лотов
                    val orders = depositManager.getOrderAllOrdersForFigi(figi, OperationType.SELL)
                    var totalSellingLots = 0
                    orders.forEach { totalSellingLots += it.requestedLots }
                    if (totalSellingLots >= lots) break

                    // заявка стоит, ничего не куплено
                    if (orderBuy != null && position == null) {
                        status = PurchaseStatus.ORDER_BUY
                        delay(DelayMiddle)
                        continue
                    }

                    if (orderBuy == null && position == null) { // заявка отменена, ничего не куплено
                        status = PurchaseStatus.NOT_FILLED
                        Utils.showToastAlert("$ticker: не налили по $buyPrice")
                        return@launch
                    }

                    if (orderBuy == null && position != null) { // заявка отменена или частично/полностью заполнена, продаём всё что куплено
                        status = PurchaseStatus.BOUGHT
                        Utils.showToastAlert("$ticker: куплено по $buyPrice")

                        // выставить ордер на продажу
                        try {
                            position = depositManager.getPositionForFigi(figi)
                            if (position == null) break

                            val lotsLeft = position.lots - position.blocked.toInt() - initialLots
                            if (lotsLeft <= 0) break
                            lotsToBuy -= lotsLeft
                            if (lotsToBuy < 0) break

                            status = PurchaseStatus.ORDER_SELL_PREPARE
                            sellLimitOrder = ordersService.placeLimitOrder(
                                lotsLeft,
                                figi,
                                profitPrice,
                                OperationType.SELL,
                                depositManager.getActiveBrokerAccountId()
                            )
                            status = PurchaseStatus.ORDER_SELL
                            Utils.showToastAlert("$ticker: ордер на продажу по $profitPrice")
                            break
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        break
                    }

                    if (orderBuy != null && position != null) { // заявка стоит, частично куплено, можно продавать
                        status = PurchaseStatus.PART_FILLED

                        // выставить ордер на продажу
                        try {
                            position = depositManager.getPositionForFigi(figi)
                            if (position == null) break

                            val lotsLeft = position.lots - position.blocked.toInt() - initialLots
                            if (lotsLeft <= 0) break
                            lotsToBuy -= lotsLeft
                            if (lotsToBuy < 0) break

                            status = PurchaseStatus.ORDER_SELL_PREPARE
                            sellLimitOrder = ordersService.placeLimitOrder(
                                lotsLeft,
                                figi,
                                profitPrice,
                                OperationType.SELL,
                                depositManager.getActiveBrokerAccountId()
                            )
                            status = PurchaseStatus.ORDER_SELL
                            Utils.showToastAlert("$ticker: ордер на продажу по $profitPrice")
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    if (lotsToBuy <= 0) break
                    delay(DelayLong)
                }

                while (true) {
                    delay(DelayLong)
                    position = depositManager.getPositionForFigi(figi)
                    if (position?.lots == initialLots) { // продано!
                        status = PurchaseStatus.SOLD
                        Utils.showToastAlert("$ticker: продано!?")
                        break
                    }
                }

            } catch (e: Exception) {
                status = PurchaseStatus.CANCELED
                e.printStackTrace()
            }
        }
    }

    fun buyLimitFromAsk(profit: Double): Job? {
        if (lots == 0) return null

        return GlobalScope.launch(Dispatchers.Main) {
            try {
                val ticker = stock.ticker
                val figi = stock.figi

                // получить лучший аск из стакана
                val orderbook = marketService.orderbook(figi, 10)
                val buyPrice = orderbook.getBestPriceFromAsk(lots)
                if (buyPrice == 0.0) return@launch

                while (true) {
                    try {
                        status = PurchaseStatus.ORDER_BUY_PREPARE
                        buyLimitOrder = ordersService.placeLimitOrder(
                            lots,
                            figi,
                            buyPrice,
                            OperationType.BUY,
                            depositManager.getActiveBrokerAccountId()
                        )
                        status = PurchaseStatus.ORDER_BUY
                        break
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    delay(DelayFast)
                }

                Utils.showToastAlert("$ticker: ордер на покупку по $buyPrice")

                if (profit == 0.0) return@launch

                // проверяем появился ли в портфеле тикер
                var position: PortfolioPosition?
                while (true) {
                    try {
                        depositManager.refreshDeposit()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    position = depositManager.getPositionForFigi(figi)
                    if (position != null && position.lots >= lots) { // куплено!
                        status = PurchaseStatus.BOUGHT
                        Utils.showToastAlert("$ticker: куплено полностью!")
                        break
                    }

                    delay(DelayLong)
                }

                // продаём
                position?.let {
                    if (profit == 0.0 || buyPrice == 0.0) return@launch
                    var profitPrice = buyPrice + buyPrice / 100.0 * profit
                    profitPrice = Utils.makeNicePrice(profitPrice)

                    // выставить ордер на продажу
                    while (true) {
                        try {
                            status = PurchaseStatus.ORDER_SELL_PREPARE
                            sellLimitOrder = ordersService.placeLimitOrder(
                                it.lots,
                                figi,
                                profitPrice,
                                OperationType.SELL,
                                depositManager.getActiveBrokerAccountId()
                            )
                            status = PurchaseStatus.ORDER_SELL
                            break
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        delay(DelayFast)
                    }

                    Utils.showToastAlert("$ticker: ордер на продажу по $profitPrice")
                }

                while (true) {
                    delay(DelayLong)

                    position = depositManager.getPositionForFigi(figi)
                    if (position == null) { // продано!
                        status = PurchaseStatus.SOLD
                        Utils.showToastAlert("$ticker: продано!?")
                        break
                    }
                }

            } catch (e: Exception) {
                status = PurchaseStatus.CANCELED
                e.printStackTrace()
            }
        }
    }

    fun buyFromAsk1728(): Job? {
        if (lots == 0) return null

        return GlobalScope.launch(Dispatchers.Main) {
            try {
                val ticker = stock.ticker
                val figi = stock.figi

                var buyPrice: Double = 0.0
                try {
                    status = PurchaseStatus.ORDER_BUY_PREPARE

                    val orderbook = marketService.orderbook(figi, 5)
                    buyPrice = orderbook.getBestPriceFromAsk(lots)
                    if (buyPrice == 0.0) return@launch

                    buyLimitOrder = ordersService.placeLimitOrder(
                        lots,
                        figi,
                        buyPrice,
                        OperationType.BUY,
                        depositManager.getActiveBrokerAccountId()
                    )
                    status = PurchaseStatus.ORDER_BUY
                } catch (e: Exception) {
                    e.printStackTrace()
                    Utils.showToastAlert("$ticker: недостаточно средств для покупки по цене $buyPrice")
                    return@launch
                }

                Utils.showToastAlert("$ticker: ордер на покупку по $buyPrice")

                delay(DelayFast)

                // проверяем появился ли в портфеле тикер
                var position: PortfolioPosition?
                while (true) {
                    try {
                        depositManager.refreshDeposit()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    position = depositManager.getPositionForFigi(figi)
                    if (position != null && position.lots >= lots) {
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
                        val orderbook = marketService.orderbook(figi, 5)
                        profitSellPrice = orderbook.getBestPriceFromBid(lots)
                        if (profitSellPrice == 0.0) return@launch

                        while (true) {
                            try {
                                profitSellPrice = Utils.makeNicePrice(profitSellPrice)
                                sellLimitOrder = ordersService.placeLimitOrder(
                                    lots,
                                    figi,
                                    profitSellPrice,
                                    OperationType.SELL,
                                    depositManager.getActiveBrokerAccountId()
                                )
                                status = PurchaseStatus.ORDER_SELL
                                break
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            delay(DelayMiddle)
                        }
                    }
                } else { // лимитка на продажу
                    position?.let {
                        val profit = SettingsManager.get1728TakeProfit()
                        if (profit == 0.0 || buyPrice == 0.0) return@launch
                        var profitPrice = buyPrice + buyPrice / 100.0 * profit
                        profitPrice = Utils.makeNicePrice(profitPrice)

                        // выставить ордер на продажу
                        while (true) {
                            try {
                                status = PurchaseStatus.ORDER_SELL_PREPARE
                                sellLimitOrder = ordersService.placeLimitOrder(
                                    it.lots,
                                    figi,
                                    profitPrice,
                                    OperationType.SELL,
                                    depositManager.getActiveBrokerAccountId()
                                )
                                status = PurchaseStatus.ORDER_SELL
                                break
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            delay(DelayMiddle)
                        }

                        Utils.showToastAlert("$ticker: ордер на продажу по $profitPrice")
                    }
                }

                while (true) {
                    position = depositManager.getPositionForFigi(figi)
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

    fun buyFromAsk2358(): Job? {
        if (lots == 0) return null

        return GlobalScope.launch(Dispatchers.Main) {
            try {
                val ticker = stock.ticker
                val figi = stock.figi

                var buyPrice: Double = 0.0
                try {
                    status = PurchaseStatus.ORDER_BUY_PREPARE
                    val orderbook = marketService.orderbook(figi, 5)
                    buyPrice = orderbook.getBestPriceFromAsk(lots)
                    if (buyPrice == 0.0) return@launch

                    buyLimitOrder = ordersService.placeLimitOrder(
                        lots,
                        figi,
                        buyPrice,
                        OperationType.BUY,
                        depositManager.getActiveBrokerAccountId()
                    )
                    status = PurchaseStatus.ORDER_BUY
                } catch (e: Exception) {
                    status = PurchaseStatus.CANCELED
                    e.printStackTrace()
                    Utils.showToastAlert("$ticker: недостаточно средств для покупки по цене $buyPrice")
                    return@launch
                }

                Utils.showToastAlert("$ticker: ордер на покупку по $buyPrice")
                delay(DelayFast)

                // проверяем появился ли в портфеле тикер
                var position: PortfolioPosition?
                while (true) {
                    try {
                        depositManager.refreshDeposit()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    position = depositManager.getPositionForFigi(figi)
                    if (position != null && position.lots >= lots) {
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
                        val orderbook = marketService.orderbook(figi, 5)
                        profitSellPrice = orderbook.getBestPriceFromBid(lots)
                        if (profitSellPrice == 0.0) return@launch

                        try {
                            profitSellPrice = Utils.makeNicePrice(profitSellPrice)
                            sellLimitOrder = ordersService.placeLimitOrder(
                                lots,
                                figi,
                                profitSellPrice,
                                OperationType.SELL,
                                depositManager.getActiveBrokerAccountId()
                            )
                            status = PurchaseStatus.ORDER_SELL
                        } catch (e: Exception) {
                            e.printStackTrace()
                            status = PurchaseStatus.ERROR_NEED_WATCH
                        }
                    }
                } else { // продажа 2358 лесенкой
                    position?.let {
                        val totalLots = it.lots
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
                            val lots = p.first
                            val profit = p.second

                            // вычисляем и округляем до 2 после запятой
                            var profitPrice = buyPrice + buyPrice / 100.0 * profit
                            profitPrice = Utils.makeNicePrice(profitPrice)

                            if (lots <= 0 || profitPrice == 0.0) continue

                            // выставить ордер на продажу
                            try {
                                sellLimitOrder = ordersService.placeLimitOrder(
                                    lots,
                                    figi,
                                    profitPrice,
                                    OperationType.SELL,
                                    depositManager.getActiveBrokerAccountId()
                                )
                                status = PurchaseStatus.ORDER_SELL
                                break
                            } catch (e: Exception) {
                                status = PurchaseStatus.ERROR_NEED_WATCH
                                e.printStackTrace()
                            }
                            delay(DelayFast)
                        }
                        Utils.showToastAlert("$ticker: ордер на продажу по $profitFrom")
                    }
                }

                while (true) {
                    delay(DelayLong)
                    if (depositManager.getPositionForFigi(figi) == null) { // продано!
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

    fun sell(): Job? {
        val figi = stock.figi
        val pos = depositManager.getPositionForFigi(figi)
        if (pos == null || pos.lots == 0 || percentProfitSellFrom == 0.0) {
            status = PurchaseStatus.CANCELED
            return null
        }

        return GlobalScope.launch(Dispatchers.Main) {
            try {
                position = pos
                val ticker = pos.stock?.ticker

                val profitPrice = getProfitPriceForSell()
                if (profitPrice == 0.0) return@launch

                var counter = 50
                while (counter > 0) {
                    try {
                        // выставить ордер на продажу
                        status = PurchaseStatus.ORDER_SELL_PREPARE
                        sellLimitOrder = ordersService.placeLimitOrder(
                            pos.lots,
                            figi,
                            profitPrice,
                            OperationType.SELL,
                            depositManager.getActiveBrokerAccountId()
                        )
                        delay(DelayMiddle)
                        depositManager.refreshOrders()
                        if (depositManager.getOrderAllOrdersForFigi(figi, OperationType.SELL).isEmpty()) continue
                        status = PurchaseStatus.ORDER_SELL

                        Utils.showToastAlert("$ticker: ордер на продажу по $profitPrice")
                        break
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    counter--
                    delay(DelayFast)
                }

                // проверяем продалось или нет
                while (true) {
                    delay(DelayLong)
                    if (depositManager.getPositionForFigi(figi) == null) { // продано!
                        status = PurchaseStatus.SOLD
                        break
                    }
                }
            } catch (e: Exception) {
                status = PurchaseStatus.CANCELED
                e.printStackTrace()
            }
        }
    }

    fun sellWithLimit(): Job? {
        val figi = stock.figi
        val pos = depositManager.getPositionForFigi(figi)
        if (pos == null || lots == 0 || percentProfitSellFrom == 0.0) {
            status = PurchaseStatus.CANCELED
            return null
        }

        return GlobalScope.launch(Dispatchers.Main) {
            try {
                position = pos
                val ticker = pos.stock?.ticker

                val profitPrice = getProfitPriceForSell()
                if (profitPrice == 0.0) return@launch

                try { // выставить ордер на продажу
                    status = PurchaseStatus.ORDER_SELL_PREPARE
                    sellLimitOrder = ordersService.placeLimitOrder(
                        lots,
                        figi,
                        profitPrice,
                        OperationType.SELL,
                        depositManager.getActiveBrokerAccountId()
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                status = PurchaseStatus.ORDER_SELL
                Utils.showToastAlert("$ticker: ордер на продажу по $profitPrice")
            } catch (e: Exception) {
                status = PurchaseStatus.CANCELED
                e.printStackTrace()
            }
        }
    }

    fun sellToBestBid(): Job? {
        val figi = stock.figi
        val pos = depositManager.getPositionForFigi(figi)
        if (pos == null || pos.lots == 0) {
            status = PurchaseStatus.CANCELED
            return null
        }

        return GlobalScope.launch(Dispatchers.Main) {
            try {
                position = pos
                status = PurchaseStatus.ORDER_SELL_PREPARE
                val ticker = pos.stock?.ticker

                val orderbook = marketService.orderbook(figi, 5)
                val bestBid = orderbook.getBestPriceFromBid(lots)
                val profitSellPrice = Utils.makeNicePrice(bestBid)

                try { // выставить ордер на продажу
                    sellLimitOrder = ordersService.placeLimitOrder(
                        lots,
                        figi,
                        profitSellPrice,
                        OperationType.SELL,
                        depositManager.getActiveBrokerAccountId()
                    )
                } catch (e: Exception) {
                    status = PurchaseStatus.CANCELED
                    e.printStackTrace()
                    return@launch
                }

                Utils.showToastAlert("$ticker: ордер на продажу по $profitSellPrice")
                status = PurchaseStatus.ORDER_SELL
            } catch (e: Exception) {
                status = PurchaseStatus.CANCELED
                e.printStackTrace()
            }
        }
    }

    fun sellToBestAsk(): Job? {
        val figi = stock.figi
        val pos = depositManager.getPositionForFigi(figi)
        if (pos == null || pos.lots == 0) {
            status = PurchaseStatus.CANCELED
            return null
        }

        return GlobalScope.launch(Dispatchers.Main) {
            try {
                position = pos
                status = PurchaseStatus.ORDER_SELL_PREPARE
                val ticker = pos.stock?.ticker

                val orderbook = marketService.orderbook(figi, 5)
                val bestAsk = orderbook.getBestPriceFromAsk(1)
                val profitSellPrice = Utils.makeNicePrice(bestAsk)

                try { // выставить ордер на продажу
                    sellLimitOrder = ordersService.placeLimitOrder(
                        lots,
                        figi,
                        profitSellPrice,
                        OperationType.SELL,
                        depositManager.getActiveBrokerAccountId()
                    )
                } catch (e: Exception) {
                    status = PurchaseStatus.CANCELED
                    e.printStackTrace()
                    return@launch
                }

                Utils.showToastAlert("$ticker: ордер на продажу по $profitSellPrice")
                status = PurchaseStatus.ORDER_SELL
            } catch (e: Exception) {
                status = PurchaseStatus.CANCELED
                e.printStackTrace()
            }
        }
    }

    fun sellWithTrailing(): Job? {
        val figi = stock.figi
        val pos = depositManager.getPositionForFigi(figi)
        if (pos == null || pos.lots == 0 || percentProfitSellFrom == 0.0) {
            status = PurchaseStatus.CANCELED
            return null
        }

        return GlobalScope.launch(Dispatchers.Main) {
            try {
                position = pos
                val ticker = pos.stock?.ticker

                currentTrailingStop = TrailingStop(stock, position.getAveragePrice(), trailingStopTakeProfitPercentActivation, trailingStopTakeProfitPercentDelta, trailingStopStopLossPercent)
                currentTrailingStop?.let {
                    strategyTrailingStop.addTrailingStop(it)
                    status = PurchaseStatus.ORDER_SELL_TRAILING

                    // вся логика ТС тут, очень долгий процесс
                    var profitSellPrice = it.process()
                    strategyTrailingStop.removeTrailingStop(it)

                    status = PurchaseStatus.ORDER_SELL_PREPARE
                    if (profitSellPrice == 0.0) return@launch

                    profitSellPrice = Utils.makeNicePrice(profitSellPrice)
                    try { // выставить ордер на продажу
                        sellLimitOrder = ordersService.placeLimitOrder(
                            lots,
                            figi,
                            profitSellPrice,
                            OperationType.SELL,
                            depositManager.getActiveBrokerAccountId()
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    Utils.showToastAlert("$ticker: ордер на продажу по $profitSellPrice")
                    status = PurchaseStatus.ORDER_SELL
                }
            } catch (e: Exception) {
                status = PurchaseStatus.CANCELED
                e.printStackTrace()
            }
        }
    }

    fun getProfitPriceForSell(): Double {
        if (percentProfitSellFrom == 0.0) return 0.0

        val avg = position.getAveragePrice()
        val priceProfit = avg + avg / 100.0 * percentProfitSellFrom
        return Utils.makeNicePrice(priceProfit)
    }

    fun processInitialProfit() {
        // по умолчанию взять профит из настроек
        var futureProfit = SettingsManager.get1000SellTakeProfit()

        // если не задан в настройках, то 1% по умолчанию
        if (futureProfit == 0.0) futureProfit = 1.0

        // если текущий профит уже больше, то за базовый взять его
        val change = position.getProfitAmount()
        val totalCash = position.balance * position.getAveragePrice()
        val currentProfit = (100.0 * change) / totalCash

        percentProfitSellFrom = if (currentProfit > futureProfit) currentProfit else futureProfit
        status = PurchaseStatus.WAITING
    }
}
