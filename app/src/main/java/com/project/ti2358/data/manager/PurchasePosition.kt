package com.project.ti2358.data.manager

import com.project.ti2358.data.model.dto.*
import com.project.ti2358.data.service.DepoManager
import com.project.ti2358.data.service.OrdersService
import com.project.ti2358.data.service.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.qualifier
import java.util.*

@KoinApiExtension
data class PurchasePosition (
    var position: PortfolioPosition
) : KoinComponent {
    private val ordersService: OrdersService by inject()
    private val depoManager: DepoManager by inject()

    var profit: Double = 0.0
    var status: PurchaseStatus = PurchaseStatus.NONE
    var sellOrder: LimitOrder? = null

    fun processInitialProfit() {
        // по умолчанию взять профит из 2358
        var futureProfit = SettingsManager.get1000TakeProfit()

        // если не задан в настройках, то 1% по умолчанию
        if (futureProfit == 0.0) futureProfit = 1.0

        // если текущий профит уже больше, то за базовый взять его
        val change = position.getProfitAmount()
        var totalCash = position.balance * position.getAveragePrice()
        var currentProfit = (100 * change) / totalCash

        profit = if (currentProfit > futureProfit) {
            currentProfit
        } else {
            futureProfit
        }
    }

    public fun getStatusString(): String =
        when (status) {
            PurchaseStatus.NONE -> ""
            PurchaseStatus.ORDER_SELL -> "ордер на продажу"
            PurchaseStatus.WAITING -> "ждём"
            PurchaseStatus.SELLED -> "продано!"
            PurchaseStatus.CANCELED -> "отменена (нет в портфеле?)"
            else -> ""
        }

    public fun getProfitPrice() : Double {
        if (profit == 0.0) return 0.0

        val avg = position.getAveragePrice()
        var priceProfit = avg + avg / 100.0 * profit
        priceProfit = Math.round(priceProfit * 100.0) / 100.0

        return priceProfit
    }

    public fun sell() {
        GlobalScope.launch(Dispatchers.Main) {
            try {
                var pos = depoManager.getPositionForFigi(position.figi)
                if (pos == null) {
                    status = PurchaseStatus.CANCELED
                    return@launch
                }

                position = pos
                if (position.lots == 0) return@launch

                if (profit == 0.0) return@launch
                var profitPrice = getProfitPrice()

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
                    delay(1000)

                    var p = depoManager.getPositionForFigi(position.figi)
                    if (p == null) { // продано!
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
