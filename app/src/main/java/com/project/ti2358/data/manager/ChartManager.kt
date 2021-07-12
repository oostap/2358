package com.project.ti2358.data.manager

import com.project.ti2358.data.tinkoff.model.Candle
import com.project.ti2358.data.tinkoff.model.Interval
import com.project.ti2358.data.tinkoff.service.MarketService
import com.project.ti2358.service.Utils
import com.project.ti2358.service.toString
import com.project.ti2358.ui.orderbook.OrderbookLine
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

@KoinApiExtension
class ChartManager() : KoinComponent {
    private val marketService: MarketService by inject()
    var orderbook: MutableList<OrderbookLine> = mutableListOf()
    var activeStock: Stock? = null

    fun start(stock: Stock?) {
        activeStock = stock
    }

    suspend fun loadCandlesForInterval(stock: Stock?, interval: Interval): List<Candle> {
        stock?.let {
            val figi = it.figi
            val ticker = it.ticker

            val zone = Utils.getTimezoneCurrent()
            val toDate = Calendar.getInstance()

            var deltaToDay = 0
            if (toDate.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                deltaToDay = -1
            }
            var deltaFromDay = 0
            if (interval in listOf(Interval.MINUTE, Interval.FIVE_MINUTES, Interval.TEN_MINUTES, Interval.FIFTEEN_MINUTES, Interval.THIRTY_MINUTES)) {
                deltaFromDay = -1
            } else if (interval in listOf(Interval.HOUR)) {
                deltaFromDay = -7
            } else if (interval in listOf(Interval.WEEK)) {
                deltaFromDay = -500
            } else if (interval in listOf(Interval.DAY)) {
                deltaFromDay = -150
            }

            toDate.add(Calendar.DAY_OF_YEAR, deltaToDay)
            val to = convertDateToTinkoffDate(toDate, zone)

            toDate.add(Calendar.DAY_OF_YEAR, deltaFromDay)
            val from = convertDateToTinkoffDate(toDate, zone)

            val intervalName = Utils.convertIntervalToString(interval)
//            log("CANDLES: TICKER $ticker FROM $from TO $to")
            return marketService.candles(figi, intervalName, from, to).candles
        }
        return emptyList()
    }

    private fun convertDateToTinkoffDate(calendar: Calendar, zone: String): String {
        return calendar.time.toString("yyyy-MM-dd'T'HH:mm:ss.SSSSSS") + zone
    }
}