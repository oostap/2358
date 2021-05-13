package com.project.ti2358.data.manager

import android.annotation.SuppressLint
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.*
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.network.fold
import com.project.ti2358.data.model.dto.*

import com.project.ti2358.data.service.OperationsService
import com.project.ti2358.data.service.OrdersService
import com.project.ti2358.service.Utils
import com.project.ti2358.service.log
import com.project.ti2358.service.toMoney
import com.project.ti2358.service.toString
import kotlinx.coroutines.*
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.sign

@KoinApiExtension
class StrategyTelegram : KoinComponent {
    private val stockManager: StockManager by inject()
    private val operationsService: OperationsService by inject()
    private val ordersService: OrdersService by inject()
    private val depositManager: DepositManager by inject()
    private val strategyFollower: StrategyFollower by inject()

    var jobUpdateOperations: Job? = null
    var operations: MutableList<Operation> = mutableListOf()
    var operationsPosted: MutableList<String> = mutableListOf()

    var jobUpdateOrders: Job? = null
    var orders: MutableList<Order> = mutableListOf()
    var ordersPosted: MutableList<String> = mutableListOf()

    var started: Boolean = false
    var telegramBot: Bot? = null

    private fun restartUpdateOperations() {
        val delay = SettingsManager.getTelegramUpdateDelay().toLong()
        jobUpdateOperations?.cancel()
        jobUpdateOperations = GlobalScope.launch(Dispatchers.Default) {
            while (true) {
                try {
                    val zone = Utils.getTimezoneCurrent()
                    val toDate = Calendar.getInstance()
                    val to = convertDateToTinkoffDate(toDate, zone)

                    toDate.add(Calendar.HOUR_OF_DAY, -6)
                    val from = convertDateToTinkoffDate(toDate, zone)

                    operations = Collections.synchronizedList(operationsService.operations(from, to, depositManager.getActiveBrokerAccountId()).operations)
                    operations.sortBy { it.date }
                    if (operationsPosted.isEmpty()) {
                        operations.forEach {
                            operationsPosted.add(it.id)
                        }
                    } else {
                        operationsPosted.add("empty")
                    }

                    depositManager.refreshDeposit()

                    for (operation in operations) {
                        if (operation.id !in operationsPosted) {
                            if (operation.status != OperationStatus.DONE || operation.quantityExecuted == 0) continue

                            operationsPosted.add(operation.id)
                            operation.stock = stockManager.getStockByFigi(operation.figi)

                            val dateNow = Calendar.getInstance()
                            val dateOperation = Calendar.getInstance()
                            dateOperation.time = operation.date
                            if (abs(dateOperation.get(Calendar.DAY_OF_YEAR) - dateNow.get(Calendar.DAY_OF_YEAR)) > 1) {
                                continue
                            }

                            sendMessageToChats(operationToString(operation))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(delay * 1000)
            }
        }
    }

    private fun restartUpdateOrders() {
        val delay = SettingsManager.getTelegramUpdateDelay().toLong()
        jobUpdateOrders?.cancel()
        jobUpdateOrders = GlobalScope.launch(Dispatchers.Default) {
            while (true) {
                try {
                    orders = Collections.synchronizedList(ordersService.orders(depositManager.getActiveBrokerAccountId()))
                    if (ordersPosted.isEmpty()) {
                        orders.forEach {
                            ordersPosted.add(it.orderId)
                        }
                    } else {
                        ordersPosted.add("empty")
                    }

                    for (order in orders) {
                        if (order.orderId !in ordersPosted) {
                            if (order.status != OrderStatus.NEW) continue

                            ordersPosted.add(order.orderId)
                            order.stock = stockManager.getStockByFigi(order.figi)

                            sendMessageToChats(orderToString(order))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(delay * 1000)
            }
        }
    }

    fun startStrategy() {
        started = true

        if (SettingsManager.getTelegramSendTrades()) restartUpdateOperations()
        if (SettingsManager.getTelegramSendOrders()) restartUpdateOrders()

        telegramBot?.stopPolling()
        telegramBot = bot {
            token = SettingsManager.getTelegramBotApiKey()
            dispatch {
                command("start") {
                    val chatId = update.message?.chat?.id ?: 0
                    val result = bot.sendMessage(chatId = ChatId.fromId(chatId), text = "Привет! Чтобы все операции приходили в нужный чат или канал, нужно прописать его айди в приложении. Чтобы узнать айди чата или канала напиши в нём: chat_id")
                    result.fold({
                        // do something here with the response
                    },{
                        // do something with the error
                    })
                    update.consume()
                }

                // сообщение в ЛС боту
                text {
                    log("chat telegram msg ${update.message?.text}")
                    val command = (update.message?.text ?: "").trim()

                    if (command == "chat_id") {
                        val text = "айди чата: ${update.message!!.chat.id}"
                        bot.sendMessage(ChatId.fromId(id = update.message!!.chat.id), text = text)
                        update.consume()
                    } else if (command == "my_id") {
                        val text = "твой айди: ${update.message!!.from?.id}"
                        bot.sendMessage(ChatId.fromId(id = update.message!!.chat.id), text = text)
                        update.consume()
                    } else if (command.startsWith("#")) {
                        if (strategyFollower.started) {
                            val success = strategyFollower.processStrategy(update.message!!.from?.id ?: 0, command)
                            val status = if (success) "+" else "-"
                            bot.sendMessage(
                                ChatId.fromId(id = update.message!!.chat.id),
                                text = status,
                                replyToMessageId = update.message!!.messageId
                            )
                        }
                        update.consume()
                    }
                }

//                // сообщение в канале
//                channel {
//                    log("channel telegram msg ${channelPost.text} ")
//                    val userText = channelPost.text ?: ""
//
//                    if (userText == "chat_id") {
//                        val text = "айди канала: ${channelPost.chat.id}"
//                        bot.sendMessage(ChatId.fromId(id = channelPost.chat.id), text = text)
//                        update.consume()
//                    } else if (userText == "my_id") {
//                        val text = "твой айди: ${channelPost.from?.id}"
//                        bot.sendMessage(ChatId.fromId(id = channelPost.chat.id), text = text)
//                        update.consume()
//                    }
//                }

                pollAnswer {
                    log("pollAnswer")
                    // do whatever you want with the answer
                }
            }
        }
        telegramBot?.startPolling()
        sendMessageToChats(SettingsManager.getTelegramHello(), deleteAfterSeconds = 10)
    }

    fun stopStrategy() {
        started = false
        sendMessageToChats(SettingsManager.getTelegramBye(), deleteAfterSeconds = 10, stop = true)
    }

    private fun convertDateToTinkoffDate(calendar: Calendar, zone: String): String {
        return calendar.time.toString("yyyy-MM-dd'T'HH:mm:ss.SSSSSS") + zone
    }

    private fun orderToString(order: Order): String {
        val ticker = order.stock?.ticker
        val orderSymbol = if (order.operation == OperationType.BUY) "🟢" else "🔴"
        var orderString = if (order.operation == OperationType.BUY) "BUY " else "SELL "
        val position = depositManager.getPositionForFigi(order.figi)
        if (position == null && order.operation == OperationType.BUY) {
            orderString += "LONG вход"
        }

        if (position == null && order.operation == OperationType.SELL) {
            orderString += "SHORT вход"
        }

        if (position != null && order.operation == OperationType.SELL) {
            orderString += if (position.lots < 0) { // продажа в шорте
                "SHORT усреднение"
            } else { // продажа в лонге
                if (order.requestedLots == abs(position.lots)) {
                    "LONG выход"
                } else {
                    "LONG выход часть"
                }
            }
        }

        if (position != null && order.operation == OperationType.BUY) {
            orderString += if (position.lots < 0) { // покупка в шорте
                if (order.requestedLots == abs(position.lots)) {
                    "SHORT выход"
                } else {
                    "SHORT выход часть"
                }
            } else { // покупка в лонге
                "LONG усреднение"
            }
        }

        var depo = ""
        position?.let {
            val percent = it.getProfitPercent() * sign(it.lots.toDouble())
            val emoji = Utils.getEmojiForPercent(percent)
            depo += "\n💼 %d * %.2f$ = %.2f$ > %.2f%%%s".format(locale = Locale.US, it.lots, it.getAveragePrice(), it.lots * it.getAveragePrice(), percent, emoji)
        }
        return "📝 $%s %s\n%s %d/%d * %.2f$ = %.2f$%s".format(locale = Locale.US, ticker, orderString, orderSymbol, order.executedLots, order.requestedLots, order.price, order.requestedLots * order.price, depo)
    }

    @SuppressLint("SimpleDateFormat")
    private fun operationToString(operation: Operation): String {
        val ticker = operation.stock?.ticker
        val operationSymbol = if (operation.operationType == OperationType.BUY) "🟢" else "🔴"
        var operationString = if (operation.operationType == OperationType.BUY) "BUY " else "SELL "
        val position = depositManager.getPositionForFigi(operation.figi)
        if (position == null && operation.operationType == OperationType.BUY) {
            operationString += "SHORT выход"
        }

        if (position == null && operation.operationType == OperationType.SELL) {
            operationString += "LONG выход"
        }

        if (position != null && operation.operationType == OperationType.SELL) {
            operationString += if (position.lots < 0) { // продажа в шорте
                if (operation.quantityExecuted == abs(position.lots)) {
                    "SHORT вход"
                } else {
                    "SHORT усреднение"
                }
            } else { // продажа в лонге
                "LONG выход часть"
            }
        }

        if (position != null && operation.operationType == OperationType.BUY) {
            operationString += if (position.lots < 0) { // покупка в шорте
                "SHORT выход часть"
            } else { // покупка в лонге
                if (operation.quantityExecuted == abs(position.lots)) {
                    "LONG вход"
                } else {
                    "LONG усреднение"
                }
            }
        }

        val msk = Utils.getTimeMSK()
        msk.time.time = operation.date.time
        val differenceHours = Utils.getTimeDiffBetweenMSK()
        msk.add(Calendar.HOUR_OF_DAY, -differenceHours)

        val dateString = msk.time.toString("HH:mm:ss")
        var depo = ""
        position?.let {
            val percent = it.getProfitPercent() * sign(it.lots.toDouble())
            val emoji = Utils.getEmojiForPercent(percent)
            depo += "\n💼 %d * %.2f$ = %.2f$ > %.2f%%%s".format(locale = Locale.US, it.lots, it.getAveragePrice(), it.lots * it.getAveragePrice(), percent, emoji)
        }
        return "$%s %s\n%s %d * %.2f$ = %.2f$ - %s%s".format(locale = Locale.US, ticker, operationString, operationSymbol, operation.quantityExecuted, operation.price, operation.quantityExecuted * operation.price, dateString, depo)
    }

    fun sendClosePriceLoaded(success: Boolean) {
        val status = if (success) "🟢" else "🔴"
        sendMessageToChats("Статус цен закрытия $status")
    }

    fun sendRocket(rocketStock: RocketStock) {
        if (started && SettingsManager.getTelegramSendRockets()) {
            val emoji = if (rocketStock.changePercent > 0) "🚀" else "☄️"
            val changePercent = if (rocketStock.changePercent > 0) {
                "+%.2f%%".format(locale = Locale.US, rocketStock.changePercent)
            } else {
                "%.2f%%".format(locale = Locale.US, rocketStock.changePercent)
            }
            val text = "$emoji$${rocketStock.ticker} ${rocketStock.priceFrom.toMoney(rocketStock.stock)} -> ${rocketStock.priceTo.toMoney(rocketStock.stock)} = $changePercent за ${rocketStock.time} мин, v = ${rocketStock.volume}"
            sendMessageToChats(text, 120)
        }
    }

    fun sendTrend(trendStock: TrendStock) {
        if (started && SettingsManager.getTelegramSendTrends()) {
            val emoji = if (trendStock.changeFromStartToLow < 0) "⤴️" else "⤵️️"
            val turnValue = if (trendStock.turnValue > 0) {
                "+%.2f%%".format(locale = Locale.US, trendStock.turnValue)
            } else {
                "%.2f%%".format(locale = Locale.US, trendStock.turnValue)
            }
            val text = "%s$%s %s : %.2f$ -> %.2f$ = %.2f%%, %.2f$ -> %.2f$ = %.2f%%, %d мин -> %d мин".format(locale = Locale.US,
                emoji,
                trendStock.ticker,
                turnValue,
                trendStock.priceStart, trendStock.priceLow, trendStock.changeFromStartToLow,
                trendStock.priceLow, trendStock.priceNow, trendStock.changeFromLowToNow,
                trendStock.timeFromStartToLow, trendStock.timeFromLowToNow)
            sendMessageToChats(text, -1)
        }
    }

    fun sendTrendStart(start: Boolean) {
        if (started && SettingsManager.getTelegramSendTrends()) {
            val text = if (start) {
                String.format(
                    locale = Locale.US,
                    "🟢⤴️⤵️️ старт: %.1f%% / %.1f%% / %d",
                    SettingsManager.getTrendMinDownPercent(),
                    SettingsManager.getTrendMinUpPercent(),
                    SettingsManager.getTrendAfterMinutes())
            } else {
                "🔴⤴️⤵️️ стоп!"
            }
            sendMessageToChats(text, 15)
        }
    }

    fun sendTazikStart(start: Boolean) {
        if (started && SettingsManager.getTelegramSendTaziks()) {
            val text = if (start) {
                String.format(
                    "🟢🛁️️ старт: %.2f%% / %.2f%% / %.2f / v%d / %ds",
                    SettingsManager.getTazikChangePercent(),
                    SettingsManager.getTazikTakeProfit(),
                    SettingsManager.getTazikApproximationFactor(),
                    SettingsManager.getTazikMinVolume(),
                    SettingsManager.getTazikOrderLifeTimeSeconds())
            } else {
                "🔴🛁 стоп!"
            }
            sendMessageToChats(text, 15)
        }
    }

    fun sendTazikEndlessStart(start: Boolean) {
        if (started && SettingsManager.getTelegramSendTaziks()) {
            val text = if (start) {
                String.format(
                    "🟢🛁♾ старт: %.2f%% / %.2f%% / %.2f / v%d / %ds / %ds",
                    SettingsManager.getTazikEndlessChangePercent(),
                    SettingsManager.getTazikEndlessTakeProfit(),
                    SettingsManager.getTazikEndlessApproximationFactor(),
                    SettingsManager.getTazikEndlessMinVolume(),
                    SettingsManager.getTazikEndlessResetIntervalSeconds(),
                    SettingsManager.getTazikEndlessOrderLifeTimeSeconds())
            } else {
                "🔴🛁♾ стоп!"
            }
            sendMessageToChats(text, 15)
        }
    }

    fun sendTazikBuy(purchase: PurchaseStock, buyPrice: Double, sellPrice: Double, priceFrom: Double, priceTo: Double, change: Double, tazikUsed: Int, tazikTotal: Int) {
        if (started && SettingsManager.getTelegramSendTaziks()) {
            val text = "🛁$%s B%.2f$ -> S%.2f$, F%.2f$ -> T%.2f$ = %.2f%%, %d/%d".format(locale = Locale.US, purchase.ticker, buyPrice, sellPrice, priceFrom, priceTo, change, tazikUsed, tazikTotal)
            sendMessageToChats(text)
        }
    }

    private fun sendMessageToChats(text: String, deleteAfterSeconds: Int = -1, stop: Boolean = false) {
        GlobalScope.launch(Dispatchers.Default) {
            try {
                val chatIds = SettingsManager.getTelegramChatID()
                for (chatId in chatIds) {
                    if (text == "") break

                    while (true) {
                        val result = telegramBot?.sendMessage(ChatId.fromId(id = chatId), text = text)
                        if (result?.first?.isSuccessful != true) {
                            delay(2500)
                            continue
                        }

                        if (deleteAfterSeconds != -1) {
                            GlobalScope.launch(Dispatchers.Default) {
                                delay(deleteAfterSeconds * 1000L)
                                val id = result.first?.body()?.result?.messageId
                                if (id != null) {
                                    telegramBot?.deleteMessage(ChatId.fromId(id = chatId), messageId = id)
                                }
                            }
                        }
                        break
                    }
                }

                if (stop) {
                    telegramBot?.stopPolling()
                    jobUpdateOperations?.cancel()
                    jobUpdateOrders?.cancel()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}