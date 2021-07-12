package com.project.ti2358.data.manager

import com.project.ti2358.data.tinkoff.model.OperationType
import com.project.ti2358.data.tinkoff.model.Order
import com.project.ti2358.data.pantini.model.PantiniPrint
import com.project.ti2358.data.tinkoff.service.OrdersService
import com.project.ti2358.service.Utils
import com.project.ti2358.ui.orderbook.OrderbookLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.lang.Exception
import kotlin.math.max

@KoinApiExtension
class OrderbookManager() : KoinComponent {
    private val stockManager: StockManager by inject()
    private val portfolioManager: PortfolioManager by inject()
    private val ordersService: OrdersService by inject()

    var activeStock: Stock? = null
    var orderbook: MutableList<OrderbookLine> = mutableListOf()

    var orderbookUS: MutableList<OrderbookLine> = mutableListOf()
    var lentaUS: MutableList<PantiniPrint> = mutableListOf()

    fun start(stock: Stock) {
        activeStock = stock
        activeStock?.let {
            // РФ стакан
            stockManager.subscribeOrderbookRU(listOf(it))

            // US стакан/лента
            stockManager.subscribeLentaUS(it)
            stockManager.subscribeOrderbookUS(it)
        }
    }

    fun stop() {
        activeStock?.let {
            // РФ стакан
            stockManager.unsubscribeOrderbookAllRU()

            // US стакан/лента
            stockManager.unsubscribeOrderbookUS(it)
            stockManager.unsubscribeStockLenta(it)
        }
        activeStock = null
    }

    fun createOrder(stock: Stock, price: Double, count: Int, operationType: OperationType) {
        GlobalScope.launch(Dispatchers.Main) {
            try {
                ordersService.placeLimitOrder(
                    count,
                    stock.figi,
                    price,
                    operationType,
                    portfolioManager.getActiveBrokerAccountId()
                )
                val operation = if (operationType == OperationType.BUY) "ПОКУПКА!" else "ПРОДАЖА!"
                Utils.showToastAlert("${stock.ticker} создан новый ордер: $operation")
            } catch (e: Exception) {

            }

            portfolioManager.refreshOrders()
            process()
        }
    }

    fun cancelOrder(order: Order) {
        GlobalScope.launch(Dispatchers.Main) {
            val operation = if (order.operation == OperationType.BUY) "ПОКУПКА!" else "ПРОДАЖА!"
            try {
                ordersService.cancel(order.orderId, portfolioManager.getActiveBrokerAccountId())
                Utils.showToastAlert("${order.stock?.ticker} ордер отменён: $operation")
            } catch (e: Exception) {
                // возможно уже отменена
                Utils.showToastAlert("${order.stock?.ticker} ордер УЖЕ отменён: $operation")
            }

            portfolioManager.refreshOrders()
            process()
        }
    }

    suspend fun cancelAllOrders() {
        for (order in portfolioManager.orders) {
            try {
                cancelOrder(order)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        portfolioManager.refreshOrders()
    }

    fun replaceOrder(from: Order, price: Double, operationType: OperationType) {
        GlobalScope.launch(Dispatchers.Main) {
            try {
                ordersService.cancel(from.orderId, portfolioManager.getActiveBrokerAccountId())

                val operation = if (from.operation == OperationType.BUY) "ПОКУПКА!" else "ПРОДАЖА!"
                Utils.showToastAlert("${from.stock?.ticker} ордер отменён: $operation")
            } catch (e: Exception) {
                // возможно уже отменена, значит двойное действие, отменить всё остальное
                portfolioManager.refreshOrders()
                process()
                return@launch
            }

            try {
                ordersService.placeLimitOrder(
                    from.requestedLots - from.executedLots,
                    from.figi,
                    price,
                    from.operation,
                    portfolioManager.getActiveBrokerAccountId()
                )

                val operation = if (from.operation == OperationType.BUY) "ПОКУПКА!" else "ПРОДАЖА!"
                Utils.showToastAlert("${from.stock?.ticker} создан новый ордер: $operation")
            } catch (e: Exception) {

            }

            portfolioManager.refreshOrders()
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

                for (order in portfolioManager.orders) {
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

    fun processUS(): MutableList<OrderbookLine> {
        orderbookUS.clear()

        activeStock?.let {
            val orderbookLocalUS = it.orderbookUS

            orderbookLocalUS?.let { book ->
                for (key in book.orderbook.keys) {
                    val pair = book.orderbook[key]
                    if (pair != null) {
                        val bidUS = pair.first
                        val askUS = pair.second

                        val line = OrderbookLine(it)
                        if (bidUS.quantity != 0) {
                            line.bidPrice = bidUS.price
                            line.bidCount = bidUS.quantity
                        }

                        if (askUS.quantity != 0) {
                            line.askPrice = askUS.price
                            line.askCount = askUS.quantity
                        }

                        line.askPercent = 100.0
                        line.bidPercent = 100.0
                        line.exchange = key

                        orderbookUS.add(line)
                    }
                }
            }
        }
        return orderbookUS
    }

    fun processUSLenta(): MutableList<PantiniPrint> {
        lentaUS.clear()

        activeStock?.let {
            lentaUS = it.lentaUS?.prints?.toMutableList() ?: mutableListOf()
        }
        return lentaUS
    }
}