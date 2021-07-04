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

    fun processALL() {
        processMACD()
    }

    fun processMACD() {
        GlobalScope.launch(Dispatchers.Main) {
            try {
                val all = process()

                all.forEach {
                    val candles = chartManager.loadCandlesForInterval(it, Interval.DAY)
                    analyticsAdapter.resetData(candles)
//                    val macd = processTurnMACD(it, candles)
                    val volume = processTurnUpVolume(it, candles)
//                    val rsi = processRSI(it, candles)

//                    if (rsi) {
//                        log("TURN UP RSI ${it.ticker}")
//                    }

//                    if (macd && volume && rsi > 0) {
//                        log("TURN UP RSI + VOLUME + MACD ${it.ticker} ")
//                    }


                    delay(400)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun processRSI(stock: Stock, candles: List<Candle>) : Int {
        if (candles.size > 10) {
            var days = 9
            for (i in candles.indices.reversed()) {
                if (candles[i].rsiOne > 75 && stock.short != null) {
                    log("TURN DOWN RSI ${stock.ticker}")
                    return -1
                }
                if (candles[i].rsiOne < 25) {
                    log("TURN UP RSI ${stock.ticker}")
                    return 1
                }
                days--
                if (days <= 0) break
            }
        }
        return 0
    }

    private fun processTurnUpVolume(stock: Stock, candles: List<Candle>) : Boolean {
        if (candles.size > 30) {
            var days = candles.size - 1
            var totalVolume = 0
            for (i in candles.indices.reversed()) {
                totalVolume += candles[i].volume
                days--
                if (days <= 0) break
            }
            val avgVolume = totalVolume / candles.size

            days = candles.size - 1
            for (i in candles.indices.reversed()) {
                val change = candles[i].closingPrice / candles[i].openingPrice * 100.0 - 100.0
                if (candles[i].volume > avgVolume * 6 && change > -5) {
                    log("TURN UP VOLUME ${stock.ticker} v = ${candles[i].volume}, date = ${candles[i].time}")
                    return true
                }
                days--
                if (days <= 0) break
            }
        }
        return false
    }

    private fun processTurnMACD(stock: Stock, candles: List<Candle>) : Boolean  {
        if (candles.isEmpty()) return false

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
            log("TURN UP MACD ${stock.ticker}  $lastMacd")
            return true
        }
        return false
    }
}