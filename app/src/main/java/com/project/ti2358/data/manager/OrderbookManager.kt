package com.project.ti2358.data.manager

import com.project.ti2358.data.model.dto.OperationType
import com.project.ti2358.data.model.dto.Order
import com.project.ti2358.data.service.OrdersService
import com.project.ti2358.ui.orderbook.OrderbookLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.lang.Exception
import kotlin.math.max
import kotlin.random.Random.Default.nextDouble
import kotlin.random.Random.Default.nextInt

@KoinApiExtension
class OrderbookManager() : KoinComponent {
    private val stockManager: StockManager by inject()
    private val depositManager: DepositManager by inject()
    private val ordersService: OrdersService by inject()

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

    fun createOrder(figi: String, price: Double, count: Int, operationType: OperationType) {
        GlobalScope.launch(Dispatchers.Main) {
            try {
                ordersService.placeLimitOrder(
                    count,
                    figi,
                    price,
                    operationType,
                    depositManager.getActiveBrokerAccountId()
                )
            } catch (e: Exception) {
                // возможно уже отменена
            }

            depositManager.refreshOrders()
            process()
        }
    }

    fun removeOrder(order: Order) {
        GlobalScope.launch(Dispatchers.Main) {
            try {
                ordersService.cancel(order.orderId, depositManager.getActiveBrokerAccountId())
            } catch (e: Exception) {
                // возможно уже отменена
            }

            depositManager.refreshOrders()
            process()
        }
    }

    fun replaceOrder(from: Order, price: Double, operationType: OperationType) {
        GlobalScope.launch(Dispatchers.Main) {
            try {
                ordersService.cancel(from.orderId, depositManager.getActiveBrokerAccountId())
            } catch (e: Exception) {
                // возможно уже отменена, значит двойное действие, отменить всё остальное
                depositManager.refreshOrders()
                process()
                return@launch
            }

            try {
                ordersService.placeLimitOrder(
                    from.requestedLots - from.executedLots,
                    from.figi,
                    price,
                    from.operation,
                    depositManager.getActiveBrokerAccountId()
                )
            } catch (e: Exception) {

            }

            depositManager.refreshOrders()
            process()
        }
    }

    fun replaceOrder(from: Order, toLine: OrderbookLine, operationType: OperationType) {
        val price = if (operationType == OperationType.BUY) {
            toLine.bidPrice
        } else {
            toLine.askPrice
        }
        replaceOrder(from, price, operationType)
    }

    fun process(): MutableList<OrderbookLine> {
        orderbook.clear()

        activeStock?.let {
            val orderbookStream = it.orderbookStream
//            val orderbookStream = OrderbookStream("123", 20,
//                listOf(listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000),
//                    listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000),
//                    listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000),
//                    listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000),
//                    listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000),
//                    listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000)),
//
//                listOf(listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000),
//                    listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000),
//                    listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000),
//                    listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000), listOf(nextDouble() * 100, nextDouble() * 10000)))

            orderbookStream?.let { book ->
                val max = max(book.asks.size, book.bids.size)
                var current = 0
                var totalAsks = 0
                var totalBids = 0
                while (current < max) {
                    val line = OrderbookLine(it)
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

                orderbook.forEach { line ->
                    line.askPercent = line.askCount.toDouble() / totalAsks
                    line.bidPercent = line.bidCount.toDouble() / totalBids
                }
            }

            orderbook.forEach { line ->
                line.ordersBuy.clear()
                line.ordersSell.clear()

                for (order in depositManager.orders) {
                    if (order.figi == line.stock.figi) {
                        if (order.price == line.askPrice || order.price == line.bidPrice) {
                            if (order.operation == OperationType.BUY) {
                                line.ordersBuy.add(order)
                            }

                            if (order.operation == OperationType.SELL) {
                                line.ordersSell.add(order)
                            }
                        }
                    }
                }
            }
        }
        return orderbook
    }
}