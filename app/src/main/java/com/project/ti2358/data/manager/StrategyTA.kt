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
        all.removeAll { it.ticker !in StrategyLove.stocksSelected.map { stock -> stock.ticker } }
        return all
    }

    fun processMACD() {
        GlobalScope.launch(Dispatchers.Main) {
            try {
                val all = process()

                all.forEach {
                    val candles = chartManager.loadCandlesForInterval(it, Interval.DAY)
                    processTurnMACD(it, candles)
                    delay(500)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun processTurnMACD(stock: Stock, candles: List<Candle>) {
        analyticsAdapter.resetData(candles)

        var steps = 2
        var bearishDays = 5

        if (candles.size > 10) {
            var lastMacd = candles.last().macd
            val coefMacd = 0.99f

            for (i in candles.indices.reversed()) {
                if (i == candles.size - 1) continue

                var success = false
                if (lastMacd < 0 && candles[i].macd < 0) {
                    success = candles[i].macd * coefMacd < lastMacd
                } else if (lastMacd > 0 && candles[i].macd > 0) {
                    success = candles[i].macd < lastMacd * coefMacd
                }

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
                    bearishDays--
                }
                checkDays--
                if (checkDays <= 0) break
            }
        }

        if (steps == 0 && bearishDays < 0) {
            log("${stock.ticker} MACD TURN UP")
        }

    }
}