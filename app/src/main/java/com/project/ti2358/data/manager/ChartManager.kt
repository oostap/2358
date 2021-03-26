package com.project.ti2358.data.manager

import com.project.ti2358.data.model.dto.Candle
import com.project.ti2358.data.model.dto.Candles
import com.project.ti2358.data.model.dto.OperationType
import com.project.ti2358.data.model.dto.Order
import com.project.ti2358.data.service.MarketService
import com.project.ti2358.data.service.OrdersService
import com.project.ti2358.service.Utils
import com.project.ti2358.service.log
import com.project.ti2358.service.toString
import com.project.ti2358.ui.orderbook.OrderbookLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.lang.Exception
import java.util.*
import kotlin.math.max

@KoinApiExtension
class ChartManager() : KoinComponent {
    private val marketService: MarketService by inject()
    private val stockManager: StockManager by inject()
    private val depositManager: DepositManager by inject()
    private val ordersService: OrdersService by inject()

    var activeStock: Stock? = null
    var orderbook: MutableList<OrderbookLine> = mutableListOf()

    fun start(stock: Stock) {
        activeStock = stock
    }

    suspend fun loadCandlesMinute1(): List<Candle> {
        activeStock?.let {
            val figi = it.figi
            val ticker = it.ticker

            val zone = Utils.getTimezoneCurrent()
            val toDate = Calendar.getInstance()
            val to = convertDateToTinkoffDate(toDate, zone)
            toDate.add(Calendar.DAY_OF_YEAR, -1)
            val from = convertDateToTinkoffDate(toDate, zone)

            log("CANDLES: TICKER $ticker FROM $from TO $to")
            return marketService.candles(figi, "1min", from, to).candles
        }
        return emptyList()
    }

    suspend fun loadCandlesMinute5(): List<Candle> {
        activeStock?.let {
            val figi = it.figi
            val ticker = it.ticker

            val zone = Utils.getTimezoneCurrent()
            val toDate = Calendar.getInstance()
            val to = convertDateToTinkoffDate(toDate, zone)
            toDate.add(Calendar.DAY_OF_YEAR, -1)
            val from = convertDateToTinkoffDate(toDate, zone)

            log("CANDLES: TICKER $ticker FROM $from TO $to")
            return marketService.candles(figi, "5min", from, to).candles
        }
        return emptyList()
    }

    suspend fun loadCandlesHour1(): List<Candle> {
        activeStock?.let {
            val figi = it.figi
            val ticker = it.ticker

            val zone = Utils.getTimezoneCurrent()
            val toDate = Calendar.getInstance()
            val to = convertDateToTinkoffDate(toDate, zone)
            toDate.add(Calendar.DAY_OF_YEAR, -7)
            val from = convertDateToTinkoffDate(toDate, zone)

            log("CANDLES: TICKER $ticker FROM $from TO $to")
            return marketService.candles(figi, "hour", from, to).candles
        }
        return emptyList()
    }

    suspend fun loadCandlesDay(): List<Candle> {
        activeStock?.let {
            val figi = it.figi
            val ticker = it.ticker

            val zone = Utils.getTimezoneCurrent()
            val toDate = Calendar.getInstance()
            val to = convertDateToTinkoffDate(toDate, zone)
            toDate.add(Calendar.DAY_OF_YEAR, -60)
            val from = convertDateToTinkoffDate(toDate, zone)

            log("CANDLES: TICKER $ticker FROM $from TO $to")
            return marketService.candles(figi, "day", from, to).candles
        }
        return emptyList()
    }

    fun convertDateToTinkoffDate(calendar: Calendar, zone: String): String {
        return calendar.time.toString("yyyy-MM-dd'T'HH:mm:ss.SSSSSS") + zone
    }
}