package com.project.ti2358.data.manager

import com.project.ti2358.data.common.BaseOrder
import com.project.ti2358.data.common.BrokerType
import com.project.ti2358.data.tinkoff.model.OperationType
import com.project.ti2358.data.pantini.model.PantiniPrint
import com.project.ti2358.ui.orderbook.OrderbookLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.max

@KoinApiExtension
class OrderbookManager() : KoinComponent {
    private val stockManager: StockManager by inject()
    private val portfolioTinkoffManager: PortfolioTinkoffManager by inject()          // убрать упоминания портфеле из всех файлов, кроме депозита
    private val portfolioAlorManager: PortfolioAlorManager by inject()
    private val brokerManager: BrokerManager by inject()

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

    fun createOrder(stock: Stock, price: Double, lots: Int, operationType: OperationType, brokerType: BrokerType) {
        GlobalScope.launch(Dispatchers.Main) {
            brokerManager.placeOrder(stock, price, lots, operationType, brokerType, true)
            withContext(StockManager.stockContext) {
                process()
            }
        }
    }

    fun cancelOrder(order: BaseOrder) {
        GlobalScope.launch(Dispatchers.Main) {
            brokerManager.cancelOrder(order, true)
            withContext(StockManager.stockContext) {
                process()
            }
        }
    }

    fun replaceOrder(from: BaseOrder, toLine: OrderbookLine, operationType: OperationType) {
        GlobalScope.launch(Dispatchers.Main) {
            val price = if (operationType == OperationType.BUY) toLine.bidPrice else toLine.askPrice

            brokerManager.replaceOrder(from, price)
            withContext(StockManager.stockContext) {
                process()
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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

            // раскидать заявки по строкам
            orderbook.forEach { line ->
                line.ordersBuy.clear()
                line.ordersSell.clear()

                val orders = brokerManager.getOrdersAll()
                for (order in orders) {
                    if (order.getOrderStock()?.ticker == line.stock.ticker) {
                        if (order.getOrderPrice() == line.askPrice || order.getOrderPrice() == line.bidPrice) {
                            if (order.getOrderOperation() == OperationType.BUY) {
                                line.ordersBuy.add(order)
                            }

                            if (order.getOrderOperation() == OperationType.SELL) {
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