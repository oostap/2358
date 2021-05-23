package com.project.ti2358.data.manager

import com.icechao.klinelib.adapter.KLineChartAdapter
import com.project.ti2358.data.model.dto.Candle
import com.project.ti2358.data.model.dto.Interval
import com.project.ti2358.service.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

@KoinApiExtension
class StrategyTA : KoinComponent {
    private val stockManager: StockManager by inject()
    private val chartManager: ChartManager by inject()
    var analyticsAdapter = KLineChartAdapter<Candle>()

    fun process(): MutableList<Stock> {
        val all = stockManager.getWhiteStocks()
//        all.removeAll { it.ticker !in StrategyLove.stocksSelected.map { stock -> stock.ticker } }
        return all
    }

    fun processMACD() {
        GlobalScope.launch(Dispatchers.Main) {
            try {
                val all = process()

                all.forEach {
                    val candles = chartManager.loadCandlesForInterval(it, Interval.DAY)
                    analyticsAdapter.resetData(candles)
                    val macd = processTurnMACD(it, candles)
                    val volume = processTurnUpVolume(it, candles)

                    if (macd && volume) {
                        log("TURN UP ${it.ticker} VOLUME + MACD")
                    }

                    delay(400)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun processTurnUpVolume(stock: Stock, candles: List<Candle>) : Boolean {
        if (candles.size > 30) {
            var days = 30
            var totalVolume = 0
            for (i in candles.indices.reversed()) {
                totalVolume += candles[i].volume
                days--
                if (days <= 0) break
            }
            val avgVolume = totalVolume / 30

            days = 30
            for (i in candles.indices.reversed()) {
                val change = candles[i].closingPrice / candles[i].openingPrice * 100.0 - 100.0
                if (candles[i].volume > avgVolume * 6 && change > -5) {
                    log("${stock.ticker} VOLUME TURN UP ! v = ${candles[i].volume}, date = ${candles[i].time}")
                    return true
                }
                days--
                if (days <= 0) break
            }
        }
        return false
    }

    private fun processTurnMACD(stock: Stock, candles: List<Candle>) : Boolean  {
        var steps = 3
        var afterBearishDays = 0

        var lastMacd = candles.last().macd

        if (candles.size > 10) {
            for (i in candles.indices.reversed()) {
                if (i == candles.size - 1) continue

                val success = candles[i].macd < lastMacd

                if (success) {
                    lastMacd = candles[i].macd
                    steps--

                    if (steps == 0) break
                } else {
                    break
                }
            }

            var checkDays = 8
            for (i in candles.indices.reversed()) {
                if (candles[i].macd < 0) {
                    afterBearishDays++
                }
                checkDays--
                if (checkDays <= 0) break
            }
        }

        if (steps == 0 && afterBearishDays < 3 && lastMacd > 0) {
            log("${stock.ticker} MACD TURN UP $lastMacd")
            return true
        }
        return false
    }
}