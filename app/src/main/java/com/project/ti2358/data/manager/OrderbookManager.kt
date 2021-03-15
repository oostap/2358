package com.project.ti2358.data.manager

import com.project.ti2358.data.model.dto.OrderbookStream
import com.project.ti2358.ui.orderbook.OrderbookLine
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.max
import kotlin.random.Random.Default.nextDouble
import kotlin.random.Random.Default.nextInt

@KoinApiExtension
class OrderbookManager() : KoinComponent {
    private val stockManager: StockManager by inject()
    var activeStock: Stock? = null
    var orderbook: MutableList<OrderbookLine> = mutableListOf()

    fun start(stock: Stock) {
        activeStock = stock
        activeStock?.let {
            stockManager.subscribeStockOrderbook(it)
        }
    }

    fun stop() {
        activeStock?.let {
            stockManager.unsubscribeStockOrderbook(it)
        }
        activeStock = null
    }

    fun process(): MutableList<OrderbookLine> {
        orderbook.clear()

        activeStock?.let {
            val orderbookStream = it.orderbookStream

//            val orderbookStream = OrderbookStream("123", 20,
//                listOf(listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000)),
//                listOf(listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000)))

            orderbookStream?.let { book ->
                val max = max(book.asks.size, book.bids.size)
                var current = 0
                var totalAsks = 0
                var totalBids = 0
                while (current < max) {
                    val line = OrderbookLine()
                    if (book.bids.size > current) {
                        val bid = book.bids[current]
                        line.bidPrice = bid[0]
                        line.bidCount = bid[1].toInt()
                        totalBids += line.bidCount
                    }

                    if (book.asks.size > current) {
                        val ask = book.asks[current]
                        line.askPrice = ask[0]
                        line.askCount = ask[1].toInt()
                        totalAsks += line.askCount
                    }

                    orderbook.add(line)
                    current++
                }

                orderbook.forEach {
                    it.askPercent = it.askCount.toDouble() / totalAsks
                    it.bidPercent = it.bidCount.toDouble() / totalBids
                }
            }
        }
        return orderbook
    }
}