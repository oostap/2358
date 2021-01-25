package com.project.ti2358.data.manager

import com.google.gson.annotations.SerializedName
import com.project.ti2358.data.model.dto.*
import com.project.ti2358.data.service.DepoManager
import com.project.ti2358.data.service.OrdersService
import com.project.ti2358.data.service.SettingsManager
import com.project.ti2358.data.service.Stock
import com.project.ti2358.service.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*
import java.util.concurrent.TimeUnit

enum class PurchaseStatus {
    NONE,
    BEFORE_BUY,
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
    private val depoManager: DepoManager by inject()

    var lots: Int = 0               // сколько штук тарим
    var startChange: Double = 0.0   // сколько % было на старте таймера
    var status: PurchaseStatus = PurchaseStatus.NONE

    public var buyOrder: MarketOrder? = null
    public var sellOrder: LimitOrder? = null

    public fun getPriceString(): String {
        return "%.1f".format(stock.todayDayCandle.closingPrice * lots) + "$"
    }

    public fun getStatusString(): String =
        when (status) {
            PurchaseStatus.NONE -> ""
            PurchaseStatus.BEFORE_BUY -> "ждём покупку"
            PurchaseStatus.ORDER_BUY -> "ордер на покупку"
            PurchaseStatus.BUYED -> "куплено!"
            PurchaseStatus.ORDER_SELL -> "ордер на продажу"
            PurchaseStatus.WAITING -> "ждём продажу"
            PurchaseStatus.SELLED -> "продано!"
            PurchaseStatus.CANCELED -> "отменена!"
        }

    public fun purchase() {
        GlobalScope.launch(Dispatchers.Main) {
            try {
                val lots = if (SettingsManager.isSandbox()) 1 else lots

                status = PurchaseStatus.ORDER_BUY
                buyOrder = ordersService.placeMarketOrder(
                    lots,
                    stock.marketInstrument.figi,
                    OperationType.BUY
                )

                // проверяем появился ли в портфеле тикер
                var position: PortfolioPosition?
                while (true) {
                    delay(1000)

                    position = depoManager.getPositionForFigi(stock.marketInstrument.figi)
                    if (position != null && position.lots >= lots) { // куплено!
                        status = PurchaseStatus.BUYED
                        break
                    }
                }

                // продаём
                position?.let {
                    val profit = SettingsManager.get2358TakeProfit()
                    if (profit == 0.0) return@launch

                    // вычисляем и округляем до 2 после запятой

                    val price: Double = if (it.averagePositionPrice.value != 0.0) {
                        it.averagePositionPrice.value
                    } else {
                        stock.todayDayCandle.closingPrice
                    }

                    var profitPrice = price + price / 100.0 * profit
                    profitPrice = Math.round(profitPrice * 100.0) / 100.0

                    if (profitPrice == 0.0) return@launch

                    // выставить ордер на продажу
                    sellOrder = ordersService.placeLimitOrder(
                        it.lots,
                        stock.marketInstrument.figi,
                        profitPrice,
                        OperationType.SELL
                    )
                    status = PurchaseStatus.ORDER_SELL
                }

                while (true) {
                    delay(1000)

                    position = depoManager.getPositionForFigi(stock.marketInstrument.figi)
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
