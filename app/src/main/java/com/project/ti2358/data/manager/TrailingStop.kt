package com.project.ti2358.data.manager

import com.project.ti2358.service.log
import com.project.ti2358.service.toMoney
import com.project.ti2358.service.toPercent
import kotlinx.coroutines.delay
import java.util.*
import kotlin.math.abs

enum class TrailingStopStatus {
    NONE,
    IDLE,
    TAKE_PROFIT_ACTIVATED,
    STOP_LOSS_ACTIVATED,
    TAKE_PROFIT_ACTIVATED_STOP,
}

data class TrailingStop(
    val stock: Stock,
    val buyPrice: Double,
    var takeProfitActivationPercent: Double,
    var takeProfitDelta: Double,
    var stopLossPercent: Double,
) {
    var started: Boolean = false
    var status: TrailingStopStatus = TrailingStopStatus.NONE
    var currentPrice = 0.0
    var currentTakeProfitPrice = 0.0
    var currentTakeProfitPercent = 0.0
    var currentChangePercent = 0.0
    var takeProfitActivationPrice: Double = 0.0

    var stopLossPrice = 0.0

    fun stop() {
        started = false
    }

    suspend fun process(): Double {
        started = true
        currentTakeProfitPrice = 0.0
        var profitSellPrice: Double = 0.0
        log("TRAILING_STOP покупка по $buyPrice, активация на $takeProfitActivationPercent%, стоп $takeProfitDelta%")

        status = TrailingStopStatus.IDLE
        stopLossPrice = buyPrice - buyPrice / 100.0 * abs(stopLossPercent)
        takeProfitActivationPrice = buyPrice + buyPrice / 100.0 * abs(takeProfitActivationPercent)

        while (started) {
            currentPrice = stock.getPriceNow()

            currentChangePercent = currentPrice / buyPrice * 100.0 - 100.0
            log("TRAILING_STOP изменение: $buyPrice -> ${currentPrice.toMoney(stock)} = ${currentChangePercent.toPercent()}")

            // активация тейкпрофита, виртуальная лимитка на -trailingStopDelta %
            if (currentTakeProfitPrice == 0.0) {
                if (currentChangePercent >= takeProfitActivationPercent) {
                    currentTakeProfitPrice = currentPrice - currentPrice / 100.0 * takeProfitDelta
                    currentTakeProfitPercent = currentChangePercent
                    log("TRAILING_STOP активация тейкпрофита, цена = ${currentTakeProfitPrice.toMoney(stock)}")
                    status = TrailingStopStatus.TAKE_PROFIT_ACTIVATED
                } else { // проверка на стоп-лосс
                    if (stopLossPercent != 0.0) { // проверка для: "0 == не создавать стоп-лосс"
                        if (currentChangePercent <= -abs(stopLossPercent)) { // если пролили, продаём по цене стоп-лосса
                            profitSellPrice = buyPrice - buyPrice / 100.0 * abs(currentChangePercent)
                            log("TRAILING_STOP активация стоп-лосса, продаём по цене = $profitSellPrice")
                            status = TrailingStopStatus.STOP_LOSS_ACTIVATED
                            break
                        }
                    }
                }
            } else { // если тейк активирован
                // если текущая цена больше тейкпрофита, то переместить лимитку выше
                if (currentPrice > currentTakeProfitPrice) {
                    val newTake = currentPrice - currentPrice / 100.0 * takeProfitDelta
                    if (newTake >= currentTakeProfitPrice) {
                        currentTakeProfitPrice = newTake
                        currentTakeProfitPercent = currentChangePercent
                        log("TRAILING_STOP поднимаем выше тейкпрофит, цена = ${currentTakeProfitPrice.toMoney(stock)}")
                    } else {
                        log("TRAILING_STOP не меняем тейкпрофит, цена = ${currentTakeProfitPrice.toMoney(stock)}")
                    }
                }

                // если текущая цена ниже тейкпрофита, то выставить лимитку по этой цене
                if (currentPrice <= currentTakeProfitPrice) {
                    log("TRAILING_STOP продаём по цене ${currentPrice.toMoney(stock)}, профит ${currentChangePercent.toPercent()}")
                    status = TrailingStopStatus.TAKE_PROFIT_ACTIVATED_STOP

                    profitSellPrice = if (currentChangePercent < takeProfitActivationPercent) {
                        buyPrice + buyPrice / 100.0 * takeProfitActivationPercent // если скачок ниже базового, то тейк на базовый
                    } else {
                        currentPrice                                                          // иначе по текущей
                    }
                    break
                }
            }
            delay(400)
        }

        return profitSellPrice
    }

    fun getDescriptionShort(): String {
        return "%s:%.2f%%".format(locale = Locale.US, stock.ticker, currentChangePercent)
    }

    fun getDescriptionLong(): String {
        val currentChange = "%.2f\$ -> %.2f\$ = %.2f%%".format(locale = Locale.US, buyPrice, currentPrice, currentChangePercent)

        val takeProfit = "%.2f$/%.2f%%".format(locale = Locale.US, takeProfitActivationPrice, takeProfitActivationPercent)

        var stopLoss = "%.2f$/%.2f%%".format(locale = Locale.US, stopLossPrice, stopLossPercent)
        if (stopLossPercent == 0.0) stopLoss = "НЕТ"

        val takeProfitRealtime = "%.2f$/%.2f%%".format(locale = Locale.US, currentTakeProfitPrice, currentTakeProfitPercent)

        return "%s: %s, ТП=%s, СЛ=%s, REALTIME=%s - %s".format(locale = Locale.US, stock.ticker, currentChange, takeProfit, stopLoss, takeProfitRealtime, getStatusString())
    }

    private fun getStatusString(): String =
        when (status) {
            TrailingStopStatus.NONE -> ""
            TrailingStopStatus.IDLE -> "считаем"
            TrailingStopStatus.TAKE_PROFIT_ACTIVATED -> "ТП активирован"
            TrailingStopStatus.STOP_LOSS_ACTIVATED -> "СЛ активирован, продаём"
            TrailingStopStatus.TAKE_PROFIT_ACTIVATED_STOP -> "ТП сработал, продаём"
        }
}