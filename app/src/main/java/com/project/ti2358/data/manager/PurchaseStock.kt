package com.project.ti2358.data.manager

import com.project.ti2358.data.model.dto.*
import com.project.ti2358.data.service.*
import com.project.ti2358.service.Utils
import com.project.ti2358.service.log
import com.project.ti2358.service.toDollar
import com.project.ti2358.service.toPercent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.abs
import kotlin.math.ceil

enum class OrderStatus {
    NONE,
    ORDER_BUY_PREPARE,
    ORDER_BUY,
    BUYED,
    ORDER_SELL_TRAILING,
    ORDER_SELL_PREPARE,
    ORDER_SELL,
    WAITING,
    SELLED,
    CANCELED,

    WTF_1,
    WTF_2,
    WTF_3,
    WTF_4,
}

@KoinApiExtension
data class PurchaseStock(
    var stock: Stock
) : KoinComponent {
    private val ordersService: OrdersService by inject()
    private val depositManager: DepositManager by inject()
    private val marketService: MarketService by inject()

    lateinit var position: PortfolioPosition
    var percentLimitPriceChange: Double = 0.0         // разница в % с текущей ценой для создания лимитки
    var absoluteLimitPriceChange: Double = 0.0        // если лимитка, то по какой цене

    var lots: Int = 0                                 // сколько штук тарим / продаём
    var status: OrderStatus = OrderStatus.NONE

    var buyMarketOrder: MarketOrder? = null
    var buyLimitOrder: LimitOrder? = null
    var sellLimitOrder: LimitOrder? = null

    var percentProfitSellFrom: Double = 0.0
    var percentProfitSellTo: Double = 0.0

    companion object {
        val DelayFast: Long = 200
        val DelayLong: Long = 2000
    }

    fun getPriceString(): String {
        return "%.1f$".format(stock.getPriceDouble() * lots)
    }

    fun getStatusString(): String =
        when (status) {
            OrderStatus.NONE -> "NONE"
            OrderStatus.WAITING -> "ждём ⏳"
            OrderStatus.ORDER_BUY_PREPARE -> "ордер: до покупки"
            OrderStatus.ORDER_BUY -> "ордер: покупка"
            OrderStatus.BUYED -> "куплено! 💸"
            OrderStatus.ORDER_SELL_TRAILING -> "Трейлинг стоп 📈"
            OrderStatus.ORDER_SELL_PREPARE -> "ордер: до продажи"
            OrderStatus.ORDER_SELL -> "ордер: продажа 🙋"
            OrderStatus.SELLED -> "продано! 🤑"
            OrderStatus.CANCELED -> "отменена! шок, скринь! 😱"
            OrderStatus.WTF_1 -> "wtf 1"
            OrderStatus.WTF_2 -> "wtf 2"
            OrderStatus.WTF_3 -> "wtf 3"
            OrderStatus.WTF_4 -> "wtf 4"
        }

    fun getLimitPriceDouble(): Double {
        val price = stock.getPriceDouble() + absoluteLimitPriceChange
        return Utils.makeNicePrice(price)
    }

    fun addPriceLimitPercent(change: Double) {
        percentLimitPriceChange += change
        updateAbsolutePrice()
    }

    fun updateAbsolutePrice() {
        absoluteLimitPriceChange = stock.getPriceDouble() / 100 * percentLimitPriceChange
        absoluteLimitPriceChange = Utils.makeNicePrice(absoluteLimitPriceChange)
    }

    fun addPriceProfit2358Percent(change: Double) {
        percentProfitSellFrom += change
        percentProfitSellTo += change
    }

    fun buyMarket(price: Double) {
        if (lots == 0) return

        val sellPrice = Utils.makeNicePrice(price)

        GlobalScope.launch(Dispatchers.Main) {
            try {
                while (true) {
                    try {
                        status = OrderStatus.ORDER_BUY_PREPARE
                        buyMarketOrder = ordersService.placeMarketOrder(
                            lots,
                            stock.marketInstrument.figi,
                            OperationType.BUY,
                            depositManager.getActiveBrokerAccountId()
                        )
                        status = OrderStatus.ORDER_BUY
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

                    position = depositManager.getPositionForFigi(stock.marketInstrument.figi)
                    if (position != null && position.lots >= lots) { // куплено!
                        status = OrderStatus.BUYED
                        break
                    }

                    delay(DelayLong)
                    counter--
                }

                // выставить ордер на продажу
                while (true) {
                    try {
                        status = OrderStatus.ORDER_SELL_PREPARE
                        position?.let {
                            sellLimitOrder = ordersService.placeLimitOrder(
                                it.lots,
                                stock.marketInstrument.figi,
                                sellPrice,
                                OperationType.SELL,
                                depositManager.getActiveBrokerAccountId()
                            )
                        }
                        status = OrderStatus.ORDER_SELL
                        break
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    delay(DelayFast)
                }

                while (true) {
                    delay(DelayLong)

                    position = depositManager.getPositionForFigi(stock.marketInstrument.figi)
                    if (position == null) { // продано!
                        status = OrderStatus.SELLED
                        break
                    }
                }

            } catch (e: Exception) {
                status = OrderStatus.CANCELED
                e.printStackTrace()
            }
        }
    }

    fun buyLimitFromBid(price: Double, profit: Double) {
        if (lots == 0) return

        val buyPrice = Utils.makeNicePrice(price)

        GlobalScope.launch(Dispatchers.Main) {
            try {

                while (true) {
                    try {
                        status = OrderStatus.ORDER_BUY_PREPARE
                        buyLimitOrder = ordersService.placeLimitOrder(
                            lots,
                            stock.marketInstrument.figi,
                            buyPrice,
                            OperationType.BUY,
                            depositManager.getActiveBrokerAccountId()
                        )
                        status = OrderStatus.ORDER_BUY
                        break
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    delay(DelayFast)
                }

                val ticker = stock.marketInstrument.ticker
                Utils.showToastAlert("$ticker: покупка по $buyPrice")

                delay(DelayFast)

                // проверяем появился ли в портфеле тикер
                var position: PortfolioPosition? = null
                var counter = 50
                while (counter > 0) {
                    try {
                        depositManager.refreshDeposit()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    position = depositManager.getPositionForFigi(stock.marketInstrument.figi)
                    if (position != null && position.lots >= lots) { // куплено!
                        status = OrderStatus.BUYED
                        break
                    }

                    delay(DelayLong)
                    counter--
                }

                // продаём
                position?.let {
                    if (profit == 0.0) return@launch

                    // вычисляем и округляем до 2 после запятой
                    if (buyPrice == 0.0) return@launch

                    var profitPrice = buyPrice + buyPrice / 100.0 * profit
                    profitPrice = Utils.makeNicePrice(profitPrice)
                    if (profitPrice == 0.0) return@launch

                    // выставить ордер на продажу
                    while (true) {
                        try {
                            status = OrderStatus.ORDER_SELL_PREPARE
                            sellLimitOrder = ordersService.placeLimitOrder(
                                it.lots,
                                stock.marketInstrument.figi,
                                profitPrice,
                                OperationType.SELL,
                                depositManager.getActiveBrokerAccountId()
                            )
                            status = OrderStatus.ORDER_SELL
                            Utils.showToastAlert("$ticker: заявка на продажу по $profitPrice")
                            break
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        delay(DelayFast)
                    }
                }

                while (true) {
                    delay(DelayLong)

                    position = depositManager.getPositionForFigi(stock.marketInstrument.figi)
                    if (position == null) { // продано!
                        status = OrderStatus.SELLED
                        Utils.showToastAlert("$ticker: продано!")
                        break
                    }
                }

            } catch (e: Exception) {
                status = OrderStatus.CANCELED
                e.printStackTrace()
            }
        }
    }

    fun buyLimitFromAsk(profit: Double) {
        if (lots == 0) return

        GlobalScope.launch(Dispatchers.Main) {
            try {
                val ticker = stock.marketInstrument.ticker

                // получить стакан
                val orderbook = marketService.orderbook(stock.marketInstrument.figi, 5)

                val buyPrice = orderbook.getBestPriceFromAsk(lots)
                log("$orderbook")

                while (true) {
                    try {
                        status = OrderStatus.ORDER_BUY_PREPARE
                        buyLimitOrder = ordersService.placeLimitOrder(
                            lots,
                            stock.marketInstrument.figi,
                            buyPrice,
                            OperationType.BUY,
                            depositManager.getActiveBrokerAccountId()
                        )
                        status = OrderStatus.ORDER_BUY
                        break
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    delay(DelayFast)
                }

                Utils.showToastAlert("$ticker: покупка по $buyPrice")
                delay(DelayFast)

                // проверяем появился ли в портфеле тикер
                var position: PortfolioPosition?
                while (true) {
                    try {
                        depositManager.refreshDeposit()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    position = depositManager.getPositionForFigi(stock.marketInstrument.figi)
                    if (position != null && position.lots >= lots) { // куплено!
                        status = OrderStatus.BUYED
                        Utils.showToastAlert("$ticker: куплено!")
                        break
                    }

                    delay(DelayLong)
                }

                // продаём
                position?.let {
                    if (profit == 0.0) return@launch

                    // вычисляем и округляем до 2 после запятой
                    if (buyPrice == 0.0) return@launch

                    var profitPrice = buyPrice + buyPrice / 100.0 * profit
                    profitPrice = Utils.makeNicePrice(profitPrice)
                    if (profitPrice == 0.0) return@launch

                    // выставить ордер на продажу
                    while (true) {
                        try {
                            status = OrderStatus.ORDER_SELL_PREPARE
                            sellLimitOrder = ordersService.placeLimitOrder(
                                it.lots,
                                stock.marketInstrument.figi,
                                profitPrice,
                                OperationType.SELL,
                                depositManager.getActiveBrokerAccountId()
                            )
                            status = OrderStatus.ORDER_SELL
                            break
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        delay(DelayFast)
                    }

                    Utils.showToastAlert("$ticker: заявка на продажу по $profitPrice")
                }

                while (true) {
                    delay(DelayLong)

                    position = depositManager.getPositionForFigi(stock.marketInstrument.figi)
                    if (position == null) { // продано!
                        status = OrderStatus.SELLED
                        Utils.showToastAlert("$ticker: продано!")
                        break
                    }
                }

            } catch (e: Exception) {
                status = OrderStatus.CANCELED
                e.printStackTrace()
            }
        }
    }

//    fun buyLimit2358() {
//        if (lots == 0) return
//
//        GlobalScope.launch(Dispatchers.Main) {
//            try {
//                var buyPrice: Double
//                while (true) {
//                    try {
//                        status = OrderStatus.ORDER_BUY_PREPARE
//                        // получить стакан
//                        val orderbook = marketService.orderbook(stock.marketInstrument.figi, 5)
//                        log("$orderbook")
//
//                        buyPrice = orderbook.getBestPriceFromAsk(lots)
//                        if (buyPrice == 0.0) return@launch
//
//                        buyLimitOrder = ordersService.placeLimitOrder(
//                            lots,
//                            stock.marketInstrument.figi,
//                            buyPrice,
//                            OperationType.BUY,
//                            depositManager.getActiveBrokerAccountId()
//                        )
//                        status = OrderStatus.ORDER_BUY
//                        break
//                    } catch (e: Exception) {
//                        e.printStackTrace()
//                    }
//                    delay(DelayFast)
//                }
//
//                val ticker = stock.marketInstrument.ticker
//                Utils.showToastAlert("$ticker: покупка по $buyPrice")
//
//                delay(DelayFast)
//
//                // проверяем появился ли в портфеле тикер
//                var position: PortfolioPosition?
//                while (true) {
//                    try {
//                        depositManager.refreshDeposit()
//                    } catch (e: Exception) {
//                        e.printStackTrace()
//                    }
//
//                    position = depositManager.getPositionForFigi(stock.marketInstrument.figi)
//                    if (position != null && position.lots >= lots) { // куплено!
//                        status = OrderStatus.BUYED
//                        Utils.showToastAlert("$ticker: куплено!")
//                        break
//                    }
//
//                    delay(DelayLong)
//                }
//
//                // продаём 2358 лесенкой
//                position?.let {
//                    val totalLots = it.lots
//                    var profitFrom = percentProfitSellFrom
//                    var profitTo = percentProfitSellTo
//
//                    if (profitFrom == 0.0) {
//                        profitFrom = SettingsManager.get2358TakeProfitFrom()
//                    }
//
//                    if (profitTo == 0.0) {
//                        profitTo = SettingsManager.get2358TakeProfitTo()
//                    }
//
//                    val profitStep = SettingsManager.get2358TakeProfitStep()
//
//                    // в случае кривых настроек просто не создаём заявки
//                    if (profitTo < profitFrom || profitStep == 0 || profitFrom == 0.0 || profitTo == 0.0) return@launch
//
//                    val list: MutableList<Pair<Int, Double>> = mutableListOf()
//                    when (profitStep) {
//                        1 -> { // если шаг 1, то создать заявку на нижний % и всё
//                            list.add(Pair(totalLots, profitFrom))
//                        }
//                        2 -> { // первый и последний
//                            val partLots1 = totalLots / 2
//                            val partLots2 = totalLots - partLots1
//                            list.add(Pair(partLots1, profitFrom))
//                            list.add(Pair(partLots2, profitTo))
//                        }
//                        else -> { // промежуточные
//                            val profitStepDouble: Double = profitStep.toDouble()
//                            val delta = (profitTo - profitFrom) / (profitStep - 1)
//
//                            // округляем в бОльшую, чтобы напоследок осталось мало лотов
//                            val basePartLots: Int = ceil(totalLots / profitStepDouble).toInt()
//
//                            var currentLots = basePartLots
//                            var currentProfit = profitFrom
//
//                            // стартовый профит
//                            list.add(Pair(basePartLots, currentProfit))
//
//                            var step = profitStep - 2
//                            while (step > 0) {
//                                if (currentLots + basePartLots > totalLots) {
//                                    break
//                                }
//                                currentLots += basePartLots
//                                currentProfit += delta
//                                list.add(Pair(basePartLots, currentProfit))
//                                step--
//                            }
//
//                            // финальный профит
//                            val lastPartLots = totalLots - currentLots
//                            if (lastPartLots > 0) {
//                                list.add(Pair(lastPartLots, profitTo))
//                            }
//                        }
//                    }
//
//                    if (list.isEmpty()) return@launch
//
//                    status = OrderStatus.ORDER_SELL_PREPARE
//                    for (p in list) {
//                        val lots = p.first
//                        val profit = p.second
//
//                        // вычисляем и округляем до 2 после запятой
//                        var profitPrice = buyPrice + buyPrice / 100.0 * profit
//                        profitPrice = Utils.makeNicePrice(profitPrice)
//
//                        if (lots <= 0 || profitPrice == 0.0) continue
//
//                        // выставить ордер на продажу
//                        while (true) {
//                            try {
//                                sellLimitOrder = ordersService.placeLimitOrder(
//                                    lots,
//                                    stock.marketInstrument.figi,
//                                    profitPrice,
//                                    OperationType.SELL,
//                                    depositManager.getActiveBrokerAccountId()
//                                )
//                                break
//                            } catch (e: Exception) {
//                                e.printStackTrace()
//                            }
//                            delay(DelayFast)
//                        }
//                    }
//                    status = OrderStatus.ORDER_SELL
//                    Utils.showToastAlert("$ticker: заявка на продажу по $profitFrom")
//                }
//
//                while (true) {
//                    delay(DelayLong)
//
//                    position = depositManager.getPositionForFigi(stock.marketInstrument.figi)
//                    if (position == null) { // продано!
//                        status = OrderStatus.SELLED
//                        Utils.showToastAlert("$ticker: продано!")
//                        break
//                    }
//                }
//
//            } catch (e: Exception) {
//                status = OrderStatus.CANCELED
//                e.printStackTrace()
//            }
//        }
//    }

    fun buyFromAsk2358WithTrailingTakeProfit() {
        if (lots == 0) return

        GlobalScope.launch(Dispatchers.Main) {
            try {
                var buyPrice: Double
                while (true) {
                    try {
                        status = OrderStatus.ORDER_BUY_PREPARE
                        // получить стакан
                        val orderbook = marketService.orderbook(stock.marketInstrument.figi, 5)
                        log("$orderbook")

                        buyPrice = orderbook.getBestPriceFromAsk(lots)
                        if (buyPrice == 0.0) return@launch

                        buyLimitOrder = ordersService.placeLimitOrder(
                            lots,
                            stock.marketInstrument.figi,
                            buyPrice,
                            OperationType.BUY,
                            depositManager.getActiveBrokerAccountId()
                        )
                        status = OrderStatus.ORDER_BUY
                        break
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    delay(DelayFast)
                }

                val ticker = stock.marketInstrument.ticker
                Utils.showToastAlert("$ticker: покупка по $buyPrice")

                delay(DelayFast)

                // проверяем появился ли в портфеле тикер
                var position: PortfolioPosition?
                while (true) {
                    try {
                        depositManager.refreshDeposit()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    position = depositManager.getPositionForFigi(stock.marketInstrument.figi)
                    if (position != null && position.lots >= lots) { // куплено!
                        status = OrderStatus.BUYED
                        Utils.showToastAlert("$ticker: куплено!")
                        break
                    }

                    delay(DelayLong)
                }

                // запускаем трейлинг стоп
                val trailingStopActivationPercent = 1.0
                val trailingStopDelta = 0.25
                var currentTakeProfitValue = 0.0

                var currentPrice = buyPrice
                var profitSellPrice = 0.0
                log("TRAILING_STOP покупка по $buyPrice, активация на $trailingStopActivationPercent%, стоп $trailingStopDelta%")

                while (true) {
                    delay(200)
                    status = OrderStatus.ORDER_SELL_TRAILING

                    val change = currentPrice - stock.getPriceDouble()

                    currentPrice = stock.getPriceDouble()
                    val currentDelta = (100 * currentPrice) / buyPrice - 100
                    log("TRAILING_STOP изменение: $buyPrice + $change -> ${currentPrice.toDollar()} = ${currentDelta.toPercent()}")
                    // активация тейкпрофита, виртуальная лимитка на -trailingStopDelta %
                    if (currentTakeProfitValue == 0.0) {
                        if (currentDelta >= trailingStopActivationPercent) {
                            currentTakeProfitValue = currentPrice - currentPrice / 100.0 * trailingStopDelta
                            log("TRAILING_STOP активация тейкпрофита, цена = ${currentTakeProfitValue.toDollar()}")
                        }
                    } else { // если тейк активирован
                        // если текущая цена больше тейкпрофита, то переместить лимитку выше
                        if (currentPrice > currentTakeProfitValue) {
                            val newTake = currentPrice - currentPrice / 100.0 * trailingStopDelta
                            if (newTake >= currentTakeProfitValue) {
                                currentTakeProfitValue = newTake
                                log("TRAILING_STOP поднимаем выше тейкпрофит, цена = ${currentTakeProfitValue.toDollar()}")
                            } else {
                                log("TRAILING_STOP не меняем тейкпрофит, цена = ${currentTakeProfitValue.toDollar()}")
                            }
                        }

                        // если текущая цена ниже тейкпрофита, то выставить лимитку по этой цене
                        if (currentPrice <= currentTakeProfitValue) {
                            log("TRAILING_STOP продаём по цене ${currentPrice.toDollar()}, профит ${currentDelta.toPercent()}")
                            profitSellPrice = currentPrice
                            break
                        }
                    }
                }

                status = OrderStatus.ORDER_SELL_PREPARE

                // выставить ордер на продажу
                while (true) {
                    try {
                        profitSellPrice = Utils.makeNicePrice(profitSellPrice)
                        sellLimitOrder = ordersService.placeLimitOrder(
                            lots,
                            stock.marketInstrument.figi,
                            profitSellPrice,
                            OperationType.SELL,
                            depositManager.getActiveBrokerAccountId()
                        )
                        break
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    delay(DelayFast)
                }
                status = OrderStatus.ORDER_SELL

                while (true) {
                    delay(DelayLong)

                    position = depositManager.getPositionForFigi(stock.marketInstrument.figi)
                    if (position == null) { // продано!
                        status = OrderStatus.SELLED
                        Utils.showToastAlert("$ticker: продано!")
                        break
                    }
                }
            } catch (e: Exception) {
                status = OrderStatus.CANCELED
                e.printStackTrace()
            }
        }
    }

    fun sell() {
        GlobalScope.launch(Dispatchers.Main) {
            try {
                val figi = stock.marketInstrument.figi
                if (figi == "") {
                    status = OrderStatus.WTF_1
                    return@launch
                }

                val pos = depositManager.getPositionForFigi(figi)
                if (pos == null) {
                    status = OrderStatus.WTF_2
                    return@launch
                }

                position = pos
                if (pos.lots == 0 || percentProfitSellFrom == 0.0) {
                    status = OrderStatus.WTF_3
                    return@launch
                }

                // скорректировать процент профита в зависимости от свечи открытия
                val startPrice = pos.stock?.candleYesterday?.closingPrice ?: 0.0
                val currentPrice = pos.stock?.candle1000?.closingPrice ?: 0.0
                if (startPrice != 0.0 && currentPrice != 0.0) {
                    val change = (100.0 * currentPrice) / startPrice - 100.0
                    if (change > percentProfitSellFrom) { // если изменение больше текущего профита, то приблизить его к этой цене
                        var delta = abs(change) - abs(percentProfitSellFrom)

                        // 0.50 коэф приближения к нижней точке, в самом низу могут не налить
                        delta *= 0.50

                        // корректируем % профита продажи
                        percentProfitSellFrom = abs(percentProfitSellFrom) + delta
                    }
                }

                val profitPrice = getProfitPriceForSell()
                if (profitPrice == 0.0) {
                    status = OrderStatus.WTF_4
                    return@launch
                }

                while (true) {
                    try {
                        // выставить ордер на продажу
                        status = OrderStatus.ORDER_SELL_PREPARE
                        sellLimitOrder = ordersService.placeLimitOrder(
                            pos.lots,
                            figi,
                            profitPrice,
                            OperationType.SELL,
                            depositManager.getActiveBrokerAccountId()
                        )
                        status = OrderStatus.ORDER_SELL
                        break
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    delay(DelayFast)
                }

                // проверяем продалось или нет
                while (true) {
                    delay(DelayLong)

                    val p = depositManager.getPositionForFigi(figi)
                    if (p == null) { // продано!
                        status = OrderStatus.SELLED
                        break
                    }
                }
            } catch (e: Exception) {
                if (status != OrderStatus.ORDER_SELL) {
                    status = OrderStatus.CANCELED
                }
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

        percentProfitSellFrom = if (currentProfit > futureProfit) {
            currentProfit
        } else {
            futureProfit
        }

        status = OrderStatus.WAITING
    }
}
