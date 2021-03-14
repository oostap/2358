package com.project.ti2358.data.manager

import com.project.ti2358.service.log
import com.project.ti2358.service.toMoney
import com.project.ti2358.service.toPercent
import kotlinx.coroutines.delay

data class TrailingTakeProfit(
    val stock: Stock,
    val buyPrice: Double,
    var trailingStopActivationPercent: Double,
    var trailingStopDelta: Double,
) {
    var currentTakeProfitValue = 0.0

    suspend fun process(): Double {
        currentTakeProfitValue = 0.0
        var currentPrice = buyPrice
        val profitSellPrice: Double
        log("TRAILING_STOP покупка по $buyPrice, активация на $trailingStopActivationPercent%, стоп $trailingStopDelta%")

        while (true) {
            delay(200)

            val change = currentPrice - stock.getPriceDouble()

            currentPrice = stock.getPriceDouble()
            val currentDeltaPercent = (100 * currentPrice) / buyPrice - 100
            log("TRAILING_STOP изменение: $buyPrice + $change -> ${currentPrice.toMoney(stock)} = ${currentDeltaPercent.toPercent()}")
            // активация тейкпрофита, виртуальная лимитка на -trailingStopDelta %
            if (currentTakeProfitValue == 0.0) {
                if (currentDeltaPercent >= trailingStopActivationPercent) {
                    currentTakeProfitValue = currentPrice - currentPrice / 100.0 * trailingStopDelta
                    log("TRAILING_STOP активация тейкпрофита, цена = ${currentTakeProfitValue.toMoney(stock)}")
                }
            } else { // если тейк активирован
                // если текущая цена больше тейкпрофита, то переместить лимитку выше
                if (currentPrice > currentTakeProfitValue) {
                    val newTake = currentPrice - currentPrice / 100.0 * trailingStopDelta
                    if (newTake >= currentTakeProfitValue) {
                        currentTakeProfitValue = newTake
                        log("TRAILING_STOP поднимаем выше тейкпрофит, цена = ${currentTakeProfitValue.toMoney(stock)}")
                    } else {
                        log("TRAILING_STOP не меняем тейкпрофит, цена = ${currentTakeProfitValue.toMoney(stock)}")
                    }
                }

                // если текущая цена ниже тейкпрофита, то выставить лимитку по этой цене
                if (currentPrice <= currentTakeProfitValue) {
                    log("TRAILING_STOP продаём по цене ${currentPrice.toMoney(stock)}, профит ${currentDeltaPercent.toPercent()}")

                    profitSellPrice = if (currentDeltaPercent < trailingStopActivationPercent) {
                        buyPrice + buyPrice / 100.0 * trailingStopActivationPercent // если скачок ниже базового, то тейк на базовый
                    } else {
                        currentPrice                                                // иначе по текущей
                    }
                    break
                }
            }
        }

        return profitSellPrice
    }

}