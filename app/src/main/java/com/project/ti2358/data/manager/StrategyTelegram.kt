package com.project.ti2358.data.manager

import android.annotation.SuppressLint
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.*
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ReplyMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.github.kotlintelegrambot.network.fold
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.project.ti2358.data.model.dto.*
import com.project.ti2358.data.model.dto.pantini.PantiniLenta
import com.project.ti2358.data.service.OperationsService
import com.project.ti2358.data.service.OrdersService
import com.project.ti2358.data.service.ThirdPartyService
import com.project.ti2358.service.Utils
import com.project.ti2358.service.log
import com.project.ti2358.service.toMoney
import com.project.ti2358.service.toString
import kotlinx.coroutines.*
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
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
    private val thirdPartyService: ThirdPartyService by inject()

    var jobUpdateOperations: Job? = null
    var operations: MutableList<Operation> = mutableListOf()
    var operationsPosted: MutableList<String> = mutableListOf()

    var jobUpdateOrders: Job? = null
    var orders: MutableList<Order> = mutableListOf()
    var ordersPosted: MutableList<String> = mutableListOf()

    var started: Boolean = false
    var telegramBot: Bot? = null

    private val gson = Gson()

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
                            if (abs(dateNow.get(Calendar.DAY_OF_YEAR) - dateOperation.get(Calendar.DAY_OF_YEAR)) >= 1) {
                                continue
                            }

                            val buttons = getButtonsMarkup(operation.stock!!)
                            sendMessageToChats(operationToString(operation), replyMarkup = buttons)
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
                inlineQuery {
                    log("TELEGRAM inlineQuery ${update.inlineQuery?.query}")
                }
                callbackQuery {
                    update.callbackQuery?.let {
                        GlobalScope.launch(Dispatchers.Default) {
                            try {
                                val json: JsonObject = gson.fromJson(it.data, JsonObject::class.java)

                                val newJson = JsonObject()
                                newJson.addProperty("method", json["m"].asString)
                                newJson.addProperty("ticker", json["t"].asString)
                                newJson.addProperty("uid", it.from.id)

                                log("data = ${newJson}")
                                thirdPartyService.oostapTelegram(newJson)
                            } catch (e: java.lang.Exception) {
                                sendMessageToChats(e.message ?: "", 60)
                                e.printStackTrace()
                            }
                        }
                    }
                }

                command("start") {
                    val chatId = update.message?.chat?.id ?: 0
                    val result = bot.sendMessage(
                        chatId = ChatId.fromId(chatId),
                        text = "Привет! Чтобы все операции приходили в нужный чат, нужно прописать его айди в приложении. Чтобы узнать айди чата напиши в нём: chat_id"
                    )
                    result.fold({
                        // do something here with the response
                    }, {
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
                    } else if (command.startsWith("!")) {
                        if (strategyFollower.started && SettingsManager.getTelegramAllowCommandHandle()) {
                            val success = strategyFollower.processActiveCommand(update.message!!.from?.id ?: 0, command)
                            val status = when (success) {
                                0 -> "-"
                                1 -> "+"
                                else -> ""
                            }
                            if (status != "") {
                                bot.sendMessage(
                                    ChatId.fromId(id = update.message!!.chat.id),
                                    text = status,
                                    replyToMessageId = update.message!!.messageId
                                )
                            }
                        }
                        update.consume()
                    } else {
                        if (strategyFollower.started && SettingsManager.getTelegramAllowCommandInform()) {
                            strategyFollower.processInfoCommand(command, update.message!!.messageId)
                        }
                    }
                }

                // сообщение в канале
                channel {
                    log("channel telegram msg ${channelPost.text} ")
                    val userText = channelPost.text ?: ""

                    if (userText == "chat_id") {
                        val text = "айди канала: ${channelPost.chat.id}"
                        bot.sendMessage(ChatId.fromId(id = channelPost.chat.id), text = text)
                        update.consume()
                    } else if (userText == "my_id") {
                        val text = "твой айди: ${channelPost.from?.id}"
                        bot.sendMessage(ChatId.fromId(id = channelPost.chat.id), text = text)
                        update.consume()
                    }
                }

                pollAnswer {
                    log("pollAnswer")
                    // do whatever you want with the answer
                }
            }
        }
        telegramBot?.startPolling()

        sendMessageToChats(SettingsManager.getTelegramHello(), deleteAfterSeconds = 10)

//        sendTest()
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
            depo += "\n💼 %d * %.2f$ = %.2f$ > %.2f%%%s".format(
                locale = Locale.US,
                it.lots,
                it.getAveragePrice(),
                it.lots * it.getAveragePrice(),
                percent,
                emoji
            )
        }
        return "📝 $%s %s\n%s %d/%d * %.2f$ = %.2f$%s".format(
            locale = Locale.US,
            ticker,
            orderString,
            orderSymbol,
            order.executedLots,
            order.requestedLots,
            order.price,
            order.requestedLots * order.price,
            depo
        )
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
            depo += "\n💼 %d * %.2f$ = %.2f$ > %.2f%%%s".format(
                locale = Locale.US,
                it.lots,
                it.getAveragePrice(),
                it.lots * it.getAveragePrice(),
                percent,
                emoji
            )
        }
        return "$%s %s\n%s %d * %.2f$ = %.2f$ - %s%s".format(
            locale = Locale.US,
            ticker,
            operationString,
            operationSymbol,
            operation.quantityExecuted,
            operation.price,
            operation.quantityExecuted * operation.price,
            dateString,
            depo
        )
    }

    fun sendClosePriceLoaded(success: Boolean) {
        val status = if (success) "🟢" else "🔴"
        sendMessageToChats("Статус цен закрытия $status")
    }

    fun sendRocket(rocketStock: RocketStock) {
        if (started && SettingsManager.getTelegramSendRockets()) {
            val emoji = when {
                rocketStock.changePercent >=  7.0 -> "‼️‼️‼️️🚀"
                rocketStock.changePercent >=  5.0 -> "❗️🚀"
                rocketStock.changePercent >=  3.0 -> "🚀🚀"
                rocketStock.changePercent >   0.0 -> "🚀"
                rocketStock.changePercent <= -7.0 -> "‼️‼️‼️️☄️"
                rocketStock.changePercent <= -5.0 -> "❗️☄️"
                rocketStock.changePercent <= -3.0 -> "☄️☄️"
                rocketStock.changePercent <   0.0 -> "☄️"
                else -> ""
            }
            val changePercent = if (rocketStock.changePercent > 0) {
                "+%.2f%%".format(locale = Locale.US, rocketStock.changePercent)
            } else {
                "%.2f%%".format(locale = Locale.US, rocketStock.changePercent)
            }
            val change2300 = "%.2f".format(rocketStock.stock.changePrice2300DayPercent)
            val text = "$emoji$${rocketStock.ticker} ${rocketStock.priceFrom.toMoney(rocketStock.stock)} -> ${rocketStock.priceTo.toMoney(rocketStock.stock)} = $changePercent за ${rocketStock.time} мин, v = ${rocketStock.volume}"
            val buttons = getButtonsMarkup(rocketStock.stock)
            sendMessageToChats(text, -1, replyMarkup = buttons)
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
            val text = "%s$%s %s : %.2f$ -> %.2f$ = %.2f%%, %.2f$ -> %.2f$ = %.2f%%, %d мин -> %d мин".format(
                locale = Locale.US,
                emoji,
                trendStock.ticker,
                turnValue,
                trendStock.priceStart, trendStock.priceLow, trendStock.changeFromStartToLow,
                trendStock.priceLow, trendStock.priceNow, trendStock.changeFromLowToNow,
                trendStock.timeFromStartToLow, trendStock.timeFromLowToNow
            )
            val buttons = getButtonsMarkup(trendStock.stock)
            sendMessageToChats(text, -1, replyMarkup = buttons)
        }
    }

    fun sendLimit(limitStock: LimitStock) {
        if (started && SettingsManager.getTelegramSendLimits()) {
            val emoji = when (limitStock.type)  {
                LimitType.ON_UP -> "⬆️ на лимите ${limitStock.stock.stockInfo?.limit_up}"
                LimitType.ON_DOWN -> "⬇️️ на лимите ${limitStock.stock.stockInfo?.limit_down}"

                LimitType.ABOVE_UP -> "⬆️ выше лимита ${limitStock.stock.stockInfo?.limit_up}"
                LimitType.UNDER_DOWN -> "⬇️️ ниже лимита ${limitStock.stock.stockInfo?.limit_down}"

                LimitType.NEAR_UP -> "⬆️ рядом с лимитом ${limitStock.stock.stockInfo?.limit_up}"
                LimitType.NEAR_DOWN -> "⬇️️ рядом с лимитом ${limitStock.stock.stockInfo?.limit_down}"
            }
            val text = "$%s %s / %.2f%% / %.2f$ -- %.2f$ -> %.2f$".format(
                locale = Locale.US,
                limitStock.ticker,
                emoji,
                limitStock.percentFire,
                limitStock.priceFire,

                limitStock.stock.getPrice2300(),
                limitStock.stock.getPriceRaw()
            )
            val buttons = getButtonsMarkup(limitStock.stock)
            sendMessageToChats(text, -1, replyMarkup = buttons)
        }
    }

    fun sendRocketStart(start: Boolean) {
        if (started && SettingsManager.getTelegramSendRockets()) {
            val text = if (start) {
                String.format(
                    locale = Locale.US,
                    "🟢🚀☄️ старт: %.2f%% / %d мин / v%d",
                    SettingsManager.getRocketChangePercent(),
                    SettingsManager.getRocketChangeMinutes(),
                    SettingsManager.getRocketChangeVolume()
                )
            } else {
                "🔴🚀☄️️ стоп!"
            }
            sendMessageToChats(text, -1)
        }
    }

    fun sendLimitsStart(start: Boolean) {
        if (started && SettingsManager.getTelegramSendRockets()) {
            val text = if (start) {
                String.format(
                    locale = Locale.US,
                    "🟢⬆️⬇️️ старт: %.2f%% / %d мин / v%d",
                    SettingsManager.getRocketChangePercent(),
                    SettingsManager.getRocketChangeMinutes(),
                    SettingsManager.getRocketChangeVolume()
                )
            } else {
                "🔴⬆️⬇️️ стоп!"
            }
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
                    SettingsManager.getTrendAfterMinutes()
                )
            } else {
                "🔴⤴️⤵️️ стоп!"
            }
            sendMessageToChats(text, -1)
        }
    }

    fun sendTazikEndlessStart(start: Boolean) {
        if (started && SettingsManager.getTelegramSendTaziks()) {
            val text = if (start) {
                String.format(
                    "🟢🛁 старт: %.2f%% / %.2f%% / %.2f / v%d / %ds / %ds",
                    SettingsManager.getTazikEndlessChangePercent(),
                    SettingsManager.getTazikEndlessTakeProfit(),
                    SettingsManager.getTazikEndlessApproximationFactor(),
                    SettingsManager.getTazikEndlessMinVolume(),
                    SettingsManager.getTazikEndlessResetIntervalSeconds(),
                    SettingsManager.getTazikEndlessOrderLifeTimeSeconds()
                )
            } else {
                "🔴🛁 стоп!"
            }
            sendMessageToChats(text, -1)
        }
    }

    fun sendZontikEndlessStart(start: Boolean) {
        if (started && SettingsManager.getTelegramSendTaziks()) {
            val text = if (start) {
                String.format(
                    "🟢☂️ старт: %.2f%% / %.2f%% / %.2f / v%d / %ds / %ds",
                    SettingsManager.getZontikEndlessChangePercent(),
                    SettingsManager.getZontikEndlessTakeProfit(),
                    SettingsManager.getZontikEndlessApproximationFactor(),
                    SettingsManager.getZontikEndlessMinVolume(),
                    SettingsManager.getZontikEndlessResetIntervalSeconds(),
                    SettingsManager.getZontikEndlessOrderLifeTimeSeconds()
                )
            } else {
                "🔴☂️ стоп!"
            }
            sendMessageToChats(text, -1)
        }
    }

    fun sendTazikBuy(purchase: PurchaseStock, buyPrice: Double, sellPrice: Double, priceFrom: Double, priceTo: Double, change: Double, tazikUsed: Int, tazikTotal: Int) {
        if (started && SettingsManager.getTelegramSendTaziks()) {
            val text = "🛁$%s B%.2f$ -> S%.2f$, F%.2f$ -> T%.2f$ = %.2f%%, %d/%d".format(
                locale = Locale.US,
                purchase.ticker,
                buyPrice,
                sellPrice,
                priceFrom,
                priceTo,
                change,
                tazikUsed,
                tazikTotal
            )
            val buttons = getButtonsMarkup(purchase.stock)
            sendMessageToChats(text, -1, replyMarkup = buttons)
        }
    }

    fun sendTazikSpike(purchase: PurchaseStock, buyPrice: Double, priceFrom: Double, priceTo: Double, change: Double, tazikUsed: Int, tazikTotal: Int) {
        if (started && SettingsManager.getTelegramSendSpikes()) {
            val text = "спайк! 🛁$%s B%.2f$, F%.2f$ -> T%.2f$ = %.2f%%, %d/%d".format(
                locale = Locale.US,
                purchase.ticker,
                buyPrice,
                priceFrom,
                priceTo,
                change,
                tazikUsed,
                tazikTotal
            )
            sendMessageToChats(text)
        }
    }

    private fun sendMessageToChats(text: String, deleteAfterSeconds: Int = -1, stop: Boolean = false, replyMarkup: ReplyMarkup? = null, replyToMessageId: Long? = null) {
        GlobalScope.launch(Dispatchers.Default) {
            try {
                val chatIds = SettingsManager.getTelegramChatID()
                for (chatId in chatIds) {
                    if (text == "") break

                    while (true) {
                        val result = telegramBot?.sendMessage(ChatId.fromId(id = chatId), text = text, replyMarkup = replyMarkup, replyToMessageId = replyToMessageId)
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

    fun getButtonsMarkup(stock: Stock): ReplyMarkup? {
        if (!SettingsManager.getTelegramSendGotoTerminal()) return null

        val ticker = stock.ticker
        val data: MutableMap<String, String> = mutableMapOf()
        data["m"] = "setTicker"
        data["t"] = ticker

        val dataJson = gson.toJson(data)
        val replyMarkup: InlineKeyboardMarkup = InlineKeyboardMarkup.createSingleRowKeyboard(
            InlineKeyboardButton.CallbackData(
                text = ticker,
                callbackData = dataJson
            )
        )
        return replyMarkup
    }

    fun getButtonsMarkupMany(stocks: List<Stock>): ReplyMarkup? {
        if (!SettingsManager.getTelegramSendGotoTerminal()) return null

        val rows = stocks.size / 4 + 1
        var index = 0
        val buttons: MutableList<MutableList<InlineKeyboardButton>> = mutableListOf()
        for (i in 0 until rows) {
            val rowButtons: MutableList<InlineKeyboardButton> = mutableListOf()
            for (j in 0 until 4) {
                if (index >= stocks.size) break

                val stock = stocks[index]
                val ticker = stock.ticker
                val data: MutableMap<String, String> = mutableMapOf()
                data["m"] = "setTicker"
                data["t"] = ticker

                val dataJson = gson.toJson(data)
                val b = InlineKeyboardButton.CallbackData(text = ticker, callbackData = dataJson)
                rowButtons.add(b)
                index++
            }
            buttons.add(rowButtons)
        }


        return InlineKeyboardMarkup.create(buttons)
    }

    fun sendStock(stock: Stock) {
        val buttons = getButtonsMarkup(stock)
        val price = stock.getPriceRaw()
        val change2300 = "%.2f".format(stock.changePrice2300DayPercent)
        sendMessageToChats("$${stock.ticker} ${price}$ / ${change2300}%", replyMarkup = buttons)
    }

    fun sendStockInfo(stock: Stock) {
        val stockInfo = stock.stockInfo
        val buttons = getButtonsMarkup(stock)
        if (stockInfo != null) {
            val price = stock.getPriceRaw()
            val percentUp = "%.2f".format(Utils.getPercentFromTo(stockInfo.limit_up, price))
            val percentDown = "%.2f".format(Utils.getPercentFromTo(stockInfo.limit_down, price))
            sendMessageToChats("$${stock.ticker} ${price}$ - ⬆️${stockInfo.limit_up}$ / ${percentUp}% ⬇️${stockInfo.limit_down}$ / ${percentDown}%", replyMarkup = buttons)
        } else {
            sendMessageToChats("$${stock.ticker} нет лимитов, current = ${stock.getPriceNow()}", replyMarkup = buttons)
        }
    }

    fun sendLentaUS(stock: Stock, lenta: PantiniLenta) {
//        val stockInfo = stock.stockInfo
//        val buttons = getButtonsMarkup(stock)
//        if (stockInfo != null) {
//            val price = stock.getPriceRaw()
//            val percentUp = "%.2f".format(Utils.getPercentFromTo(stockInfo.limit_up, price))
//            val percentDown = "%.2f".format(Utils.getPercentFromTo(stockInfo.limit_down, price))
//            sendMessageToChats("$${stock.ticker} ${price}$ - ⬆️${stockInfo.limit_up}$ / ${percentUp}% ⬇️${stockInfo.limit_down}$ / ${percentDown}%", replyMarkup = buttons)
//        } else {
//            sendMessageToChats("$${stock.ticker} нет лимитов, current = ${stock.getPriceNow()}", replyMarkup = buttons)
//        }
    }

    fun sendPulse(messageId: Long) {
        GlobalScope.launch(Dispatchers.Main) {
            val phrase = stockManager.getPulsePhrase()
            sendMessageToChats(phrase, replyToMessageId = messageId)
        }
    }

    fun sendTop(stocks: List<Stock>, count: Int) {
        var text = ""
        for (i in 0 until count) {
            val stock = stocks[i]
            text += "$%s %4.2f$ -> %4.2f$ = %4.2f%%\n".format(stock.ticker, stock.getPrice2300(), stock.getPriceRaw(), stock.changePrice2300DayPercent)
        }
        val buttons = getButtonsMarkupMany(stocks.subList(0, count))
        sendMessageToChats(text, -1, replyMarkup = buttons)
    }

    fun sendTest() {
        GlobalScope.launch(Dispatchers.Default) {
            delay(1000)
            val ticker = stockManager.stocksStream.random().ticker
            val data: MutableMap<String, String> = mutableMapOf()
            data["m"] = "setTicker"
            data["t"] = ticker

            val dataJson = gson.toJson(data)
            val replyMarkup: InlineKeyboardMarkup = InlineKeyboardMarkup.createSingleRowKeyboard(
                InlineKeyboardButton.CallbackData(text = ticker, callbackData = dataJson)
            )

            sendMessageToChats(ticker, deleteAfterSeconds = -1, replyMarkup = replyMarkup)
        }
    }
}