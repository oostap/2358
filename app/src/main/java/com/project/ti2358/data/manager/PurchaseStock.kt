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
            PurchaseStatus.CANCELED -> "отменена!"
        }

    fun getLimitPriceDouble(): Double {
        var price = stock.getPriceDouble() + absoluteLimitPriceChange
        price = (price * 100.0).roundToInt() / 100.0
        return price
    }

    fun addPriceLimitPercent(change: Double) {
        percentLimitPriceChange += change
        updateAbsolutePrice()
    }

    fun updateAbsolutePrice() {
        absoluteLimitPriceChange = stock.getPriceDouble() / 100 * percentLimitPriceChange
        absoluteLimitPriceChange = (absoluteLimitPriceChange * 100.0).roundToInt() / 100.0
    }

    fun buyMarket(priceSell: Double) {
        if (SettingsManager.isSandbox() || lots == 0) return

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

                // продаём
                position?.let {
                    // выставить ордер на продажу
                    sellLimitOrder = ordersService.placeLimitOrder(
                        it.lots,
                        stock.marketInstrument.figi,
                        priceSell,
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

    fun buyLimitFromBid(buyPrice: Double, profit: Double) {
        if (SettingsManager.isSandbox() || lots == 0) return

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
                    profitPrice = (profitPrice * 100.0).roundToInt() / 100.0
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
                e.printStackTrace()
            }
        }
    }
}
