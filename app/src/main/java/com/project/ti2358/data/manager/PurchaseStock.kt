package com.project.ti2358.data.manager

import com.project.ti2358.data.model.dto.*
import com.project.ti2358.data.service.*
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
import kotlin.math.roundToInt

enum class PurchaseStatus {
    NONE,
    ORDER_BUY,
    BUYED,
    ORDER_SELL,
    WAITING,
    SELLED,
    CANCELED,
}

@KoinApiExtension
data class PurchaseStock(
    var stock: Stock
) : KoinComponent {
    private val ordersService: OrdersService by inject()
    private val depositManager: DepositManager by inject()
    private val marketService: MarketService by inject()

    var percentLimitPriceChange: Double = 0.0         // разница в % с текущей ценой для создания лимитки
    var absoluteLimitPriceChange: Double = 0.0        // если лимитка, то по какой цене

    var lots: Int = 0                   // сколько штук тарим
    var status: PurchaseStatus = PurchaseStatus.NONE

    var buyMarketOrder: MarketOrder? = null
    var buyLimitOrder: LimitOrder? = null

    var sellLimitOrder: LimitOrder? = null

    fun getPriceString(): String {
        return "%.1f$".format(stock.getPriceDouble() * lots)
    }

    fun getStatusString(): String =
        when (status) {
            PurchaseStatus.NONE -> ""
            PurchaseStatus.WAITING -> "ждём"
            PurchaseStatus.ORDER_BUY -> "ордер: покупка"
            PurchaseStatus.BUYED -> "куплено!"
            PurchaseStatus.ORDER_SELL -> "ордер: продажа"
            PurchaseStatus.SELLED -> "продано!"
            PurchaseStatus.CANCELED -> "отменена! (шок, скринь!)"
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

    fun buyMarket(price: Double) {
        if (SettingsManager.isSandbox() || lots == 0) return

        val sellPrice = Utils.makeNicePrice(price)

        GlobalScope.launch(Dispatchers.Main) {
            try {
                status = PurchaseStatus.ORDER_BUY
                buyMarketOrder = ordersService.placeMarketOrder(
                    lots,
                    stock.marketInstrument.figi,
                    OperationType.BUY
                )

                // проверяем появился ли в портфеле тикер
                var position: PortfolioPosition? = null
                var counter = 50
                while (counter > 0) {
                    depositManager.refreshDeposit()

                    position = depositManager.getPositionForFigi(stock.marketInstrument.figi)
                    if (position != null && position.lots >= lots) { // куплено!
                        status = PurchaseStatus.BUYED
                        break
                    }

                    delay(2000)
                    counter--
                }

                // выставить ордер на продажу
                position?.let {
                    sellLimitOrder = ordersService.placeLimitOrder(
                        it.lots,
                        stock.marketInstrument.figi,
                        sellPrice,
                        OperationType.SELL
                    )
                    status = PurchaseStatus.ORDER_SELL
                }

                while (true) {
                    delay(2000)

                    position = depositManager.getPositionForFigi(stock.marketInstrument.figi)
                    if (position == null) { // продано!
                        status = PurchaseStatus.SELLED
                        break
                    }
                }

            } catch (e: Exception) {
                status = PurchaseStatus.CANCELED
                e.printStackTrace()
            }
        }
    }

    fun buyLimitFromBid(price: Double, profit: Double) {
        if (SettingsManager.isSandbox() || lots == 0) return

        val buyPrice = Utils.makeNicePrice(price)

        GlobalScope.launch(Dispatchers.Main) {
            try {
                status = PurchaseStatus.ORDER_BUY
                buyLimitOrder = ordersService.placeLimitOrder(
                    lots,
                    stock.marketInstrument.figi,
                    buyPrice,
                    OperationType.BUY
                )

                val ticker = stock.marketInstrument.ticker
                Utils.showToastAlert("$ticker: покупка по $buyPrice")

                delay(250)

                // проверяем появился ли в портфеле тикер
                var position: PortfolioPosition? = null
                var counter = 50
                while (counter > 0) {
                    depositManager.refreshDeposit()

                    position = depositManager.getPositionForFigi(stock.marketInstrument.figi)
                    if (position != null && position.lots >= lots) { // куплено!
                        status = PurchaseStatus.BUYED
                        break
                    }

                    delay(1000)
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
                    sellLimitOrder = ordersService.placeLimitOrder(
                        it.lots,
                        stock.marketInstrument.figi,
                        profitPrice,
                        OperationType.SELL
                    )
                    Utils.showToastAlert("$ticker: заявка на продажу по $profitPrice")
                    status = PurchaseStatus.ORDER_SELL
                }

                while (true) {
                    delay(3000)

                    position = depositManager.getPositionForFigi(stock.marketInstrument.figi)
                    if (position == null) { // продано!
                        status = PurchaseStatus.SELLED
                        Utils.showToastAlert("$ticker: продано!")
                        break
                    }
                }

            } catch (e: Exception) {
                status = PurchaseStatus.CANCELED
                e.printStackTrace()
            }
        }
    }

    fun buyLimitFromAsk(profit: Double) {
        if (SettingsManager.isSandbox() || lots == 0) return

        GlobalScope.launch(Dispatchers.Main) {
            try {
                // получить стакан
                val orderbook = marketService.orderbook(stock.marketInstrument.figi, 5)

                val buyPrice = orderbook.getBestPriceFromAsk(lots)
                log("$orderbook")

                status = PurchaseStatus.ORDER_BUY
                buyLimitOrder = ordersService.placeLimitOrder(
                    lots,
                    stock.marketInstrument.figi,
                    buyPrice,
                    OperationType.BUY
                )

                val ticker = stock.marketInstrument.ticker
                Utils.showToastAlert("$ticker: покупка по $buyPrice")

                delay(250)

                // проверяем появился ли в портфеле тикер
                var position: PortfolioPosition?
                while (true) {
                    depositManager.refreshDeposit()

                    position = depositManager.getPositionForFigi(stock.marketInstrument.figi)
                    if (position != null && position.lots >= lots) { // куплено!
                        status = PurchaseStatus.BUYED
                        Utils.showToastAlert("$ticker: куплено!")
                        break
                    }

                    delay(1000)
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
                    sellLimitOrder = ordersService.placeLimitOrder(
                        it.lots,
                        stock.marketInstrument.figi,
                        profitPrice,
                        OperationType.SELL
                    )
                    status = PurchaseStatus.ORDER_SELL

                    Utils.showToastAlert("$ticker: заявка на продажу по $profitPrice")
                }

                while (true) {
                    delay(3000)

                    position = depositManager.getPositionForFigi(stock.marketInstrument.figi)
                    if (position == null) { // продано!
                        status = PurchaseStatus.SELLED
                        Utils.showToastAlert("$ticker: продано!")
                        break
                    }
                }

            } catch (e: Exception) {
                status = PurchaseStatus.CANCELED
                e.printStackTrace()
            }
        }
    }

    fun buyLimit2358() {
        if (SettingsManager.isSandbox() || lots == 0) return

        GlobalScope.launch(Dispatchers.Main) {
            try {
                // получить стакан
                val orderbook = marketService.orderbook(stock.marketInstrument.figi, 5)
                log("$orderbook")

                val buyPrice = orderbook.getBestPriceFromAsk(lots)
                if (buyPrice == 0.0) return@launch

                status = PurchaseStatus.ORDER_BUY
                buyLimitOrder = ordersService.placeLimitOrder(
                    lots,
                    stock.marketInstrument.figi,
                    buyPrice,
                    OperationType.BUY
                )

                val ticker = stock.marketInstrument.ticker
                Utils.showToastAlert("$ticker: покупка по $buyPrice")

                delay(250)

                // проверяем появился ли в портфеле тикер
                var position: PortfolioPosition?
                while (true) {
                    depositManager.refreshDeposit()

                    position = depositManager.getPositionForFigi(stock.marketInstrument.figi)
                    if (position != null && position.lots >= lots) { // куплено!
                        status = PurchaseStatus.BUYED
                        Utils.showToastAlert("$ticker: куплено!")
                        break
                    }

                    delay(1000)
                }

                // продаём 2358 лесенкой
                position?.let {
                    val totalLots = it.lots
                    val profitFrom = SettingsManager.get2358TakeProfitFrom()
                    val profitTo = SettingsManager.get2358TakeProfitTo()
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
                            val delta = (profitTo - profitFrom) / (profitStep - 1)
                            val basePartLots = totalLots / profitStep

                            var currentLots = basePartLots
                            var currentProfit = profitFrom

                            // стартовый профит
                            list.add(Pair(basePartLots, currentProfit))

                            var step = profitStep - 2
                            while (step > 0) {
                                currentLots += basePartLots
                                currentProfit += delta
                                list.add(Pair(basePartLots, currentProfit))
                                step--
                            }

                            // финальный профит
                            val lastPartLots = totalLots - currentLots
                            list.add(Pair(lastPartLots, profitTo))
                        }
                    }

                    if (list.isEmpty()) return@launch

                    for (p in list) {
                        val lots = p.first
                        val profit = p.second

                        // вычисляем и округляем до 2 после запятой
                        var profitPrice = buyPrice + buyPrice / 100.0 * profit
                        profitPrice = Utils.makeNicePrice(profitPrice)

                        if (lots <= 0 || profitPrice == 0.0) continue

                        // выставить ордер на продажу
                        sellLimitOrder = ordersService.placeLimitOrder(
                            lots,
                            stock.marketInstrument.figi,
                            profitPrice,
                            OperationType.SELL
                        )
                    }

                    status = PurchaseStatus.ORDER_SELL
                    Utils.showToastAlert("$ticker: заявка на продажу по $profitFrom")
                }

                while (true) {
                    delay(3000)

                    position = depositManager.getPositionForFigi(stock.marketInstrument.figi)
                    if (position == null) { // продано!
                        status = PurchaseStatus.SELLED
                        Utils.showToastAlert("$ticker: продано!")
                        break
                    }
                }

            } catch (e: Exception) {
                status = PurchaseStatus.CANCELED
                e.printStackTrace()
            }
        }
    }
}
