package com.project.ti2358.data.manager

import com.project.ti2358.data.model.dto.*
import com.project.ti2358.data.service.OrdersService
import com.project.ti2358.data.service.SettingsManager
import com.project.ti2358.service.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

@KoinApiExtension
data class PurchasePosition(
    var position: PortfolioPosition
) : KoinComponent {
    private val ordersService: OrdersService by inject()
    private val depositManager: DepositManager by inject()

    var profit: Double = 0.0
    var status: PurchaseStatus = PurchaseStatus.NONE
    var sellOrder: LimitOrder? = null

    fun processInitialProfit() {
        // по умолчанию взять профит из настроек
        var futureProfit = SettingsManager.get1000SellTakeProfit()

        // если не задан в настройках, то 1% по умолчанию
        if (futureProfit == 0.0) futureProfit = 1.0

        // если текущий профит уже больше, то за базовый взять его
        val change = position.getProfitAmount()
        val totalCash = position.balance * position.getAveragePrice()
        val currentProfit = (100.0 * change) / totalCash

        profit = if (currentProfit > futureProfit) {
            currentProfit
        } else {
            futureProfit
        }
    }

    fun getStatusString(): String =
        when (status) {
            PurchaseStatus.NONE -> ""
            PurchaseStatus.ORDER_SELL -> "ордер на продажу"
            PurchaseStatus.WAITING -> "ждём"
            PurchaseStatus.SELLED -> "продано!"
            PurchaseStatus.CANCELED -> "отменена (нет в портфеле?)"
            else -> ""
        }

    fun getProfitPrice(): Double {
        if (profit == 0.0) return 0.0

        val avg = position.getAveragePrice()
        val priceProfit = avg + avg / 100.0 * profit
        return Utils.makeNicePrice(priceProfit)
    }

    fun sell() {
        GlobalScope.launch(Dispatchers.Main) {
            try {
                val pos = depositManager.getPositionForFigi(position.figi)
                if (pos == null) {
                    status = PurchaseStatus.CANCELED
                    return@launch
                }

                position = pos
                if (position.lots == 0 || profit == 0.0) return@launch

                // скорректировать процент профита в зависимости от свечи открытия
                val startPrice = position.stock?.candleYesterday?.closingPrice ?: 0.0
                val currentPrice = position.stock?.candle1000?.closingPrice ?: 0.0
                if (startPrice != 0.0 && currentPrice != 0.0) {
                    val change = (100.0 * currentPrice) / startPrice - 100.0
                    if (change > profit) { // если изменение больше текущего профита, то приблизить его к этой цене
                        var delta = abs(change) - abs(profit)

                        // 0.50 коэф приближения к нижней точке, в самом низу могут не налить
                        delta *= 0.50

                        // корректируем % профита продажи
                        profit = abs(profit) + delta
                    }
                }

                val profitPrice = getProfitPrice()
                if (profitPrice == 0.0) return@launch

                // выставить ордер на продажу
                sellOrder = ordersService.placeLimitOrder(
                    position.lots,
                    position.figi,
                    profitPrice,
                    OperationType.SELL
                )
                status = PurchaseStatus.ORDER_SELL

                // проверяем продалось или нет
                while (true) {
                    delay(2000)

                    val p = depositManager.getPositionForFigi(position.figi)
                    if (p == null) { // продано!
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
}
