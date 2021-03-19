package com.project.ti2358.data.manager

import com.project.ti2358.service.log
import com.project.ti2358.service.toMoney
import com.project.ti2358.service.toPercent
import kotlinx.coroutines.delay
import kotlin.math.abs

data class TrailingStop(
    val stock: Stock,
    val buyPrice: Double,
    var trailingStopTakeProfitActivationPercent: Double,
    var trailingStopTakeProfitDelta: Double,
    var trailingStopStopLoss: Double,
) {
    var currentTakeProfitValue = 0.0

    suspend fun process(): Double {
        currentTakeProfitValue = 0.0
        var currentPrice = buyPrice
        val profitSellPrice: Double
        log("TRAILING_STOP покупка по $buyPrice, активация на $trailingStopTakeProfitActivationPercent%, стоп $trailingStopTakeProfitDelta%")

        while (true) {
            delay(200)

            val change = currentPrice - stock.getPriceDouble()

            currentPrice = stock.getPriceDouble()
            val currentDeltaPercent = (100 * currentPrice) / buyPrice - 100
            log("TRAILING_STOP изменение: $buyPrice + $change -> ${currentPrice.toMoney(stock)} = ${currentDeltaPercent.toPercent()}")

            // активация тейкпрофита, виртуальная лимитка на -trailingStopDelta %
            if (currentTakeProfitValue == 0.0) {
                if (currentDeltaPercent >= trailingStopTakeProfitActivationPercent) {
                    currentTakeProfitValue = currentPrice - currentPrice / 100.0 * trailingStopTakeProfitDelta
                    log("TRAILING_STOP активация тейкпрофита, цена = ${currentTakeProfitValue.toMoney(stock)}")
                } else { // проверка на стоп-лосс
                    if (trailingStopStopLoss != 0.0) { // проверка для: "0 == не создавать стоп-лосс"
                        if (currentDeltaPercent <= trailingStopStopLoss) { // если пролили, продаём по цене стоп-лосса
                            profitSellPrice = buyPrice - buyPrice / 100.0 * abs(currentDeltaPercent)
                            log("TRAILING_STOP активация стоп-лосса, продаём по цене = $profitSellPrice")
                            break
                        }
                    }
                }
            } else { // если тейк активирован
                // если текущая цена больше тейкпрофита, то переместить лимитку выше
                if (currentPrice > currentTakeProfitValue) {
                    val newTake = currentPrice - currentPrice / 100.0 * trailingStopTakeProfitDelta
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

                    profitSellPrice = if (currentDeltaPercent < trailingStopTakeProfitActivationPercent) {
                        buyPrice + buyPrice / 100.0 * trailingStopTakeProfitActivationPercent // если скачок ниже базового, то тейк на базовый
                    } else {
                        currentPrice                                                          // иначе по текущей
                    }
                    break
                }
            }
        }

        return profitSellPrice
    }

}