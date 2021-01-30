package com.project.ti2358.data.service

import com.project.ti2358.data.model.dto.Candle
import com.project.ti2358.data.model.dto.Interval
import com.project.ti2358.data.model.dto.MarketInstrument
import io.reactivex.rxjava3.kotlin.subscribeBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@KoinApiExtension
class StockManager() : KoinComponent {
    private val marketService: MarketService by inject()
    private val streamingService: StreamingService by inject()

    private var instrumentsAll: List<MarketInstrument> = emptyList()
    private var stocksAll: MutableList<Stock> = mutableListOf()

    // все акции, которые участвуют в расчётах с учётом базовой сортировки из настроек
    var stocksStream: MutableList<Stock> = mutableListOf()
    var loadClosingPriceDelay: Long = 0

    public fun loadStocks() {
        if (instrumentsAll.isNotEmpty()) return

        GlobalScope.launch(Dispatchers.Main) {
            try {
                instrumentsAll = marketService.stocks().instruments

                stocksAll.clear()
                for (instrument in instrumentsAll) {
                    stocksAll.add(Stock(instrument))
                }
                baseSortStocks()

                resetSubscription()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    public fun getStockByFigi(figi: String) : Stock? {
        for (stock in stocksAll) {
            if (stock.marketInstrument.figi == figi) {
                return stock
            }
        }
        return null
    }

    private fun baseSortStocks() {
        stocksStream.clear()

        for (stock in stocksAll) {
            if (SettingsManager.isAllowCurrency(stock.marketInstrument.currency)) {
                stocksStream.add(stock)
            }
        }
    }

    private fun resetSubscription() {
        stocksStream.let { stocks ->

            streamingService
                .getCandleEventStream(
                    stocks.map { it.marketInstrument.figi },
                    Interval.DAY
                )
                .subscribeBy(
                    onNext = {
                        addCandle(it)
                    },
                    onError = {
                        it.printStackTrace()
                    }
                )

            streamingService
                .getCandleEventStream(
                    stocks.map { it.marketInstrument.figi },
                    Interval.MINUTE
                )
                .subscribeBy(
                    onNext = {
                        addCandle(it)
                    },
                    onError = {
                        it.printStackTrace()
                    }
                )
        }
    }

    public fun unsubscribeStock(stock: Stock, interval: Interval) {
        streamingService.getCandleEventStream(listOf(stock.marketInstrument.figi), interval)

        // получить цену закрытия US
        loadClosingPriceDelay = stock.loadClosingPriceCandle(loadClosingPriceDelay)
    }

    private fun addCandle(candle: Candle) {
        for (stock in stocksStream) {
            if (stock.marketInstrument.figi == candle.figi) {
                stock.processCandle(candle)
            }
        }
    }
}
