package com.project.ti2358.data.manager

import com.project.ti2358.data.model.dto.*
import com.project.ti2358.data.service.*
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
data class PurchaseStock (
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
            PurchaseStatus.ORDER_BUY -> "ордер: покупка"
            PurchaseStatus.BUYED -> "куплено!"
            PurchaseStatus.ORDER_SELL -> "ордер: продажа"
            PurchaseStatus.WAITING -> "ждём"
            PurchaseStatus.SELLED -> "продано!"
            PurchaseStatus.CANCELED -> "отменена!"
        }

    fun getLimitPriceDouble(): Double {
        var price = stock.getPriceDouble() + absoluteLimitPriceChange
        price = (price * 100.0).roundToInt() / 100.0
        return price
    }

    fun addPriceLimitPercent(change: Double) {
        percentLimitPriceChange += change
        absoluteLimitPriceChange = stock.getPriceDouble() / 100 * percentLimitPriceChange
        absoluteLimitPriceChange = (absoluteLimitPriceChange * 100.0).roundToInt() / 100.0
    }

    fun buyMarket() { // unused
        GlobalScope.launch(Dispatchers.Main) {
            try {
                val lots = if (SettingsManager.isSandbox()) 1 else lots

                status = PurchaseStatus.ORDER_BUY
                buyMarketOrder = ordersService.placeMarketOrder(
                    lots,
                    stock.marketInstrument.figi,
                    OperationType.BUY
                )

                // проверяем появился ли в портфеле тикер
                var position: PortfolioPosition?
                while (true) {
                    position = depositManager.getPositionForFigi(stock.marketInstrument.figi)
                    if (position != null && position.lots >= lots) { // куплено!
                        status = PurchaseStatus.BUYED
                        break
                    }

                    delay(500)
                }

                // продаём
                position?.let {
                    val profit = SettingsManager.get2358TakeProfit()
                    if (profit == 0.0) return@launch

                    // вычисляем и округляем до 2 после запятой
                    val price = stock.getPriceDouble()
                    if (price == 0.0) return@launch

                    var profitPrice = price + price / 100.0 * profit
                    profitPrice = (profitPrice * 100.0).roundToInt() / 100.0
                    if (profitPrice == 0.0) return@launch

                    // выставить ордер на продажу
                    sellLimitOrder = ordersService.placeLimitOrder(
                        it.lots,
                        stock.marketInstrument.figi,
                        profitPrice,
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
                e.printStackTrace()
            }
        }
    }

    fun buyLimit() {
        if (SettingsManager.isSandbox()) return

        GlobalScope.launch(Dispatchers.Main) {
            try {
                val lots = lots

                status = PurchaseStatus.ORDER_BUY
                val buyPrice = getLimitPriceDouble()
                buyLimitOrder = ordersService.placeLimitOrder(
                    lots,
                    stock.marketInstrument.figi,
                    buyPrice,
                    OperationType.BUY
                )

                // проверяем появился ли в портфеле тикер
                var position: PortfolioPosition?
                while (true) {
                    position = depositManager.getPositionForFigi(stock.marketInstrument.figi)
                    if (position != null && position.lots >= lots) { // куплено!
                        status = PurchaseStatus.BUYED
                        break
                    }

                    delay(250)
                }

                // продаём
                position?.let {
                    val profit = SettingsManager.get1000BuyTakeProfit()
                    if (profit == 0.0) return@launch

                    // вычисляем и округляем до 2 после запятой
                    if (buyPrice == 0.0) return@launch

                    var profitPrice = buyPrice + buyPrice / 100.0 * profit
                    profitPrice = (profitPrice * 100.0).roundToInt() / 100.0
                    if (profitPrice == 0.0) return@launch

                    // выставить ордер на продажу
                    sellLimitOrder = ordersService.placeLimitOrder(
                        it.lots,
                        stock.marketInstrument.figi,
                        profitPrice,
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
                e.printStackTrace()
            }
        }
    }

    fun buyLimitFromAsk1728() {
        if (SettingsManager.isSandbox()) return

        GlobalScope.launch(Dispatchers.Main) {
            try {
                // получить стакан
                val orderbook = marketService.orderbook(stock.marketInstrument.figi, 5)

                val volume = SettingsManager.get1728PurchaseVolume()
                lots = (volume / stock.getPriceDouble()).roundToInt()

                val buyPrice = orderbook.getBestPriceFromAsk(lots)
                log("$orderbook")

                status = PurchaseStatus.ORDER_BUY
                buyLimitOrder = ordersService.placeLimitOrder(
                    lots,
                    stock.marketInstrument.figi,
                    buyPrice,
                    OperationType.BUY
                )

                depositManager.refreshDeposit()

                // проверяем появился ли в портфеле тикер
                var position: PortfolioPosition?
                while (true) {
                    position = depositManager.getPositionForFigi(stock.marketInstrument.figi)
                    if (position != null && position.lots >= lots) { // куплено!
                        status = PurchaseStatus.BUYED
                        break
                    }

                    delay(100)
                }

                // продаём
                position?.let {
                    val profit = SettingsManager.get1728TakeProfit()
                    if (profit == 0.0) return@launch

                    // вычисляем и округляем до 2 после запятой
                    if (buyPrice == 0.0) return@launch

                    var profitPrice = buyPrice + buyPrice / 100.0 * profit
                    profitPrice = (profitPrice * 100.0).roundToInt() / 100.0
                    if (profitPrice == 0.0) return@launch

                    // выставить ордер на продажу
                    sellLimitOrder = ordersService.placeLimitOrder(
                        it.lots,
                        stock.marketInstrument.figi,
                        profitPrice,
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
                e.printStackTrace()
            }
        }
    }

    fun buyLimitFromAsk2358() {
        if (SettingsManager.isSandbox()) return

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

                depositManager.refreshDeposit()

                // проверяем появился ли в портфеле тикер
                var position: PortfolioPosition?
                while (true) {
                    position = depositManager.getPositionForFigi(stock.marketInstrument.figi)
                    if (position != null && position.lots >= lots) { // куплено!
                        status = PurchaseStatus.BUYED
                        break
                    }

                    delay(100)
                }

                // продаём
                position?.let {
                    val profit = SettingsManager.get2358TakeProfit()
                    if (profit == 0.0) return@launch

                    // вычисляем и округляем до 2 после запятой
                    if (buyPrice == 0.0) return@launch

                    var profitPrice = buyPrice + buyPrice / 100.0 * profit
                    profitPrice = (profitPrice * 100.0).roundToInt() / 100.0
                    if (profitPrice == 0.0) return@launch

                    // выставить ордер на продажу
                    sellLimitOrder = ordersService.placeLimitOrder(
                        it.lots,
                        stock.marketInstrument.figi,
                        profitPrice,
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
                e.printStackTrace()
            }
        }
    }
}
