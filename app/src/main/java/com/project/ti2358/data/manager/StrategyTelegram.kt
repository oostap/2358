package com.project.ti2358.data.manager

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.*
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.ReplyMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.github.kotlintelegrambot.network.fold
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.project.ti2358.TheApplication
import com.project.ti2358.data.alor.model.AlorOrder
import com.project.ti2358.data.common.BaseOrder
import com.project.ti2358.data.common.BasePosition
import com.project.ti2358.data.common.BrokerType
import com.project.ti2358.data.daager.service.ThirdPartyService
import com.project.ti2358.data.pantini.model.PantiniLenta
import com.project.ti2358.data.tinkoff.model.*
import com.project.ti2358.data.tinkoff.service.OperationsService
import com.project.ti2358.service.*
import kotlinx.coroutines.*
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign


@KoinApiExtension
class StrategyTelegram : KoinComponent {
    private val stockManager: StockManager by inject()
    private val brokerManager: BrokerManager by inject()
    private val operationsService: OperationsService by inject()

    private val tinkoffPortfolioManager: TinkoffPortfolioManager by inject()
    private val alorPortfolioManager: AlorPortfolioManager by inject()

    private val strategyTelegramCommands: StrategyTelegramCommands by inject()
    private val thirdPartyService: ThirdPartyService by inject()

    var jobUpdateOperations: Job? = null
    var operations: MutableList<Operation> = mutableListOf()
    var operationsPosted: MutableList<String> = mutableListOf()

    var jobUpdateOrders: Job? = null
    var orders: MutableList<TinkoffOrder> = mutableListOf()
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

                    operations = Collections.synchronizedList(
                        operationsService.operations(
                            from,
                            to,
                            tinkoffPortfolioManager.getActiveBrokerAccountId()
                        ).operations
                    )
                    operations.sortBy { it.date }
                    if (operationsPosted.isEmpty()) {
                        operations.forEach {
                            operationsPosted.add(it.id)
                        }
                    } else {
                        operationsPosted.add("empty")
                    }

                    tinkoffPortfolioManager.refreshDeposit()

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
                    brokerManager.refreshOrders()
                    val orders = brokerManager.getOrdersAll()
                    if (ordersPosted.isEmpty()) {
                        orders.forEach {
                            ordersPosted.add(it.getOrderID())
                        }
                    } else {
                        ordersPosted.add("empty")
                    }

                    for (order in orders) {
                        if (order.getOrderID() !in ordersPosted) {
                            if (!order.isCreated()) continue // незавершенная

                            ordersPosted.add(order.getOrderID())
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
                    update.message?.let {
                        val dateMsg = Calendar.getInstance()
                        dateMsg.time = Date(it.date * 1000)
                        val dateNow = Calendar.getInstance()
                        val delta = (dateNow.time.time - dateMsg.time.time) / 1000

                        val command = (it.text ?: "").trim()

                        if (command == "! sync") { // суперкоманда
                            GlobalScope.launch(Dispatchers.Main) {
                                stockManager.processStocks()
                                Utils.showToastAlert("Всё перезагружено!")
                            }
                            return@let
                        }

                        if (abs(delta) > 300) { // если сообщение устарело на 300 секунд = 5 минут - игнорируем всю обработку
                            return@let          // также игнорится, если время на телефоне настроено НЕВЕРНО
                        }

                        log("chat telegram msg ${it.text} delta = ${delta}")

                        if (command == "chat_id") {
                            val text = "айди чата: ${it.chat.id}"
                            bot.sendMessage(ChatId.fromId(id = it.chat.id), text = text)
                            update.consume()
                        } else if (command == "my_id") {
                            val text = "твой айди: ${it.from?.id}"
                            bot.sendMessage(ChatId.fromId(id = it.chat.id), text = text)
                            update.consume()
                        } else if (command.startsWith("!")) {
                            if (strategyTelegramCommands.started && SettingsManager.getTelegramAllowCommandHandle()) {
                                strategyTelegramCommands.processActiveCommand(it.from?.id ?: 0, command)
                            }
                            update.consume()
                        } else {
                            if (strategyTelegramCommands.started && SettingsManager.getTelegramAllowCommandInform()) {
                                val delete = strategyTelegramCommands.processInfoCommand(command, it.messageId)
                                if (delete) {
                                    telegramBot?.deleteMessage(ChatId.fromId(id = it.chat.id), messageId = it.messageId)
                                }
                            }
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

    private fun orderToString(order: BaseOrder): String {
        val orderSymbol = if (order.getOrderOperation() == OperationType.BUY) "🟢" else "🔴"
        var orderString = if (order.getOrderOperation() == OperationType.BUY) "BUY " else "SELL "

        var position: BasePosition? = null
        if (order is TinkoffOrder) {
            val stock = stockManager.getStockByFigi(order.figi) ?: return ""
            position = tinkoffPortfolioManager.getPositionForStock(stock)
            order.stock = stock
        }

        if (order is AlorOrder) {
            val stock = stockManager.getStockByTicker(order.symbol) ?: return ""
            position = alorPortfolioManager.getPositionForStock(stock)
            order.stock = stock
        }

        val ticker = order.getOrderStock()?.ticker

        if (position == null && order.getOrderOperation() == OperationType.BUY) {
            orderString += "LONG вход"
        }

        if (position == null && order.getOrderOperation() == OperationType.SELL) {
            orderString += "SHORT вход"
        }

        if (position != null && order.getOrderOperation() == OperationType.SELL) {
            orderString += if (position.getLots() < 0) { // продажа в шорте
                "SHORT усреднение"
            } else { // продажа в лонге
                if (order.getLotsRequested() == abs(position.getLots())) {
                    "LONG выход"
                } else {
                    "LONG выход часть"
                }
            }
        }

        if (position != null && order.getOrderOperation() == OperationType.BUY) {
            orderString += if (position.getLots() < 0) { // покупка в шорте
                if (order.getLotsRequested() == abs(position.getLots())) {
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
            val percent = it.getProfitPercent() * sign(it.getLots().toDouble())
            val emoji = Utils.getEmojiForPercent(percent)
            depo += "\n💼 %d * %.2f$ = %.2f$ > %.2f%%%s".format(
                locale = Locale.US,
                it.getLots(),
                it.getAveragePrice(),
                it.getLots() * it.getAveragePrice(),
                percent,
                emoji
            )
        }
        return "📝 $%s %s\n%s %d/%d * %.2f$ = %.2f$%s".format(
            locale = Locale.US,
            ticker,
            orderString,
            orderSymbol,
            order.getLotsExecuted(),
            order.getLotsRequested(),
            order.getOrderPrice(),
            order.getLotsRequested() * order.getOrderPrice(),
            depo
        )
    }

    @SuppressLint("SimpleDateFormat")
    private fun operationToString(operation: Operation): String {
        if (operation.stock == null) return ""

        val ticker = operation.stock?.ticker
        val operationSymbol = if (operation.operationType == OperationType.BUY) "🟢" else "🔴"
        var operationString = if (operation.operationType == OperationType.BUY) "BUY " else "SELL "
        val position = tinkoffPortfolioManager.getPositionForStock(operation.stock!!)
        if (position == null && operation.operationType == OperationType.BUY) {
            operationString += "SHORT выход"
        }

        if (position == null && operation.operationType == OperationType.SELL) {
            operationString += "LONG выход"
        }

        if (position != null && operation.operationType == OperationType.SELL) {
            operationString += if (position.getLots() < 0) { // продажа в шорте
                if (operation.quantityExecuted == abs(position.getLots())) {
                    "SHORT вход"
                } else {
                    "SHORT усреднение"
                }
            } else { // продажа в лонге
                "LONG выход часть"
            }
        }

        if (position != null && operation.operationType == OperationType.BUY) {
            operationString += if (position.getLots() < 0) { // покупка в шорте
                "SHORT выход часть"
            } else { // покупка в лонге
                if (operation.quantityExecuted == abs(position.getLots())) {
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
            val percent = it.getProfitPercent() * sign(it.getLots().toDouble())
            val emoji = Utils.getEmojiForPercent(percent)
            depo += "\n💼 %d * %.2f$ = %.2f$ > %.2f%%%s".format(
                locale = Locale.US,
                it.getLots(),
                it.getAveragePrice(),
                it.getLots() * it.getAveragePrice(),
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
        sendMessageToChats("Статус цен закрытия $status", deleteAfterSeconds = 5)
    }

    fun sendRocket(stockRocket: StockRocket) {
        if (started && SettingsManager.getTelegramSendRockets()) {
            val emoji = when {
                stockRocket.changePercent >=  7.0 -> "‼️‼️‼️️🚀"
                stockRocket.changePercent >=  5.0 -> "❗️🚀"
                stockRocket.changePercent >=  3.0 -> "🚀🚀"
                stockRocket.changePercent >   0.0 -> "🚀"
                stockRocket.changePercent <= -7.0 -> "‼️‼️‼️️☄️"
                stockRocket.changePercent <= -5.0 -> "❗️☄️"
                stockRocket.changePercent <= -3.0 -> "☄️☄️"
                stockRocket.changePercent <   0.0 -> "☄️"
                else -> ""
            }
            val changePercent = if (stockRocket.changePercent > 0) {
                "+%.2f%%".format(locale = Locale.US, stockRocket.changePercent)
            } else {
                "%.2f%%".format(locale = Locale.US, stockRocket.changePercent)
            }
            val change2300 = "%.2f".format(stockRocket.stock.changePrice2300DayPercent)
            val text = "$emoji$${stockRocket.ticker} ${stockRocket.priceFrom.toMoney(stockRocket.stock)} -> ${stockRocket.priceTo.toMoney(stockRocket.stock)} = $changePercent за ${stockRocket.time} мин, v = ${stockRocket.volume}"
            val buttons = getButtonsMarkup(stockRocket.stock)
            sendMessageToChats(text, 120, replyMarkup = buttons)
        }
    }

    fun sendTrend(stockTrend: StockTrend) {
        if (started && SettingsManager.getTelegramSendTrends()) {
            val emoji = if (stockTrend.changeFromStartToLow < 0) "⤴️" else "⤵️️"
            val turnValue = if (stockTrend.turnValue > 0) {
                "+%.2f%%".format(locale = Locale.US, stockTrend.turnValue)
            } else {
                "%.2f%%".format(locale = Locale.US, stockTrend.turnValue)
            }
            val text = "%s$%s %s : %.2f$ -> %.2f$ = %.2f%%, %.2f$ -> %.2f$ = %.2f%%, %d мин -> %d мин".format(
                locale = Locale.US,
                emoji,
                stockTrend.ticker,
                turnValue,
                stockTrend.priceStart, stockTrend.priceLow, stockTrend.changeFromStartToLow,
                stockTrend.priceLow, stockTrend.priceNow, stockTrend.changeFromLowToNow,
                stockTrend.timeFromStartToLow, stockTrend.timeFromLowToNow
            )
            val buttons = getButtonsMarkup(stockTrend.stock)
            sendMessageToChats(text, 120, replyMarkup = buttons)
        }
    }

    fun sendLimit(stockLimit: StockLimit) {
        if (started && SettingsManager.getTelegramSendLimits()) {
            var emoji = when (stockLimit.type)  {
                LimitType.ON_UP -> "⬆️ на лимите ${stockLimit.stock.stockInfo?.limit_up}$"
                LimitType.ON_DOWN -> "⬇️️ на лимите ${stockLimit.stock.stockInfo?.limit_down}$"

                LimitType.ABOVE_UP -> "⬆️ выше лимита ${stockLimit.stock.stockInfo?.limit_up}$"
                LimitType.UNDER_DOWN -> "⬇️️ ниже лимита ${stockLimit.stock.stockInfo?.limit_down}$"

                LimitType.NEAR_UP -> "⬆️ рядом с лимитом ${stockLimit.stock.stockInfo?.limit_up}$"
                LimitType.NEAR_DOWN -> "⬇️️ рядом с лимитом ${stockLimit.stock.stockInfo?.limit_down}$"
            }

            emoji += " / %.2f%%".format(stockLimit.percentFire)
            val text = "$%s %.2f$ - %s".format(
                locale = Locale.US,
                stockLimit.ticker,
                stockLimit.priceFire,
                emoji
            )
            val buttons = getButtonsMarkup(stockLimit.stock)
            sendMessageToChats(text, 120, replyMarkup = buttons)
        }
    }

    fun sendRocketStart(start: Boolean) {
        if (started && SettingsManager.getTelegramSendRockets()) {
            val text = if (start) {
                String.format(
                    locale = Locale.US,
                    "🟢🚀☄️ %.2f%% / %d мин / v%d",
                    SettingsManager.getRocketChangePercent(),
                    SettingsManager.getRocketChangeMinutes(),
                    SettingsManager.getRocketChangeVolume()
                )
            } else {
                "🔴🚀☄️️ стоп!"
            }
            sendMessageToChats(text, 15)
        }
    }

    fun sendArbitrationStart(start: Boolean) {
        if (started) {
            val text = if (start) {
                String.format(
                    locale = Locale.US,
                    "🟢🏴‍☠️ v%d / v%d",
                    SettingsManager.getArbitrationVolumeDayFrom(),
                    SettingsManager.getArbitrationVolumeDayTo()
                )
            } else {
                "🔴🏴‍☠️ стоп!"
            }
            sendMessageToChats(text, 15)
        }
    }

    fun sendLimitsStart(start: Boolean) {
        if (started && SettingsManager.getTelegramSendRockets()) {
            val text = if (start) {
                String.format(
                    locale = Locale.US,
                    "🟢⬆️⬇️️ %.2f%% / %d мин / v%d",
                    SettingsManager.getRocketChangePercent(),
                    SettingsManager.getRocketChangeMinutes(),
                    SettingsManager.getRocketChangeVolume()
                )
            } else {
                "🔴⬆️⬇️️ стоп!"
            }
            sendMessageToChats(text, 15)
        }
    }

    fun sendTrendStart(start: Boolean) {
        if (started && SettingsManager.getTelegramSendTrends()) {
            val text = if (start) {
                String.format(
                    locale = Locale.US,
                    "🟢⤴️⤵️️ %.1f%% / %.1f%% / %d",
                    SettingsManager.getTrendMinDownPercent(),
                    SettingsManager.getTrendMinUpPercent(),
                    SettingsManager.getTrendAfterMinutes()
                )
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
                    SettingsManager.getTazikOrderLifeTimeSeconds()
                )
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
                    "🟢🛁 %.2f%% / %.2f%% / %.2f / v%d / %ds / %ds / %.2f%%",
                    SettingsManager.getTazikEndlessChangePercent(),
                    SettingsManager.getTazikEndlessTakeProfit(),
                    SettingsManager.getTazikEndlessApproximationFactor(),
                    SettingsManager.getTazikEndlessMinVolume(),
                    SettingsManager.getTazikEndlessResetIntervalSeconds(),
                    SettingsManager.getTazikEndlessOrderLifeTimeSeconds(),
                    SettingsManager.getTazikEndlessClosePriceProtectionPercent()
                )
            } else {
                "🔴🛁 стоп!"
            }
            sendMessageToChats(text, 15)
        }
    }

    fun send2358Start(start: Boolean, tickers: List<String>) {
        if (started) {// && SettingsManager.getTelegramSendTaziks()) {
            val text = if (start) {
                String.format("🟢 2358 тарим ${tickers.joinToString(" ")} на ${SettingsManager.get2358PurchaseVolume()}$")
            } else {
                "🔴 2358 не тарим ${tickers.joinToString(" ")}"
            }
            sendMessageToChats(text, -1)
        }
    }

    fun send2358DayLowStart(start: Boolean, tickers: List<String>) {
        if (started) {// && SettingsManager.getTelegramSendTaziks()) {
            val text = if (start) {
                String.format("🟢 2358DL тарим ${tickers.joinToString(" ")} на ${SettingsManager.get2358PurchaseVolume()}$")
            } else {
                "🔴 2358DL не тарим ${tickers.joinToString(" ")}"
            }
            sendMessageToChats(text, -1)
        }
    }

    fun send2225Start(start: Boolean, tickers: List<String>) {
        if (started) {// && SettingsManager.getTelegramSendTaziks()) {
            val text = if (start) {
                String.format("🟢 2225 шортим ${tickers.joinToString(" ")} на ${SettingsManager.get2225PurchaseVolume()}$")
            } else {
                "🔴 2225 отмена шорта ${tickers.joinToString(" ")}"
            }
            sendMessageToChats(text, -1)
        }
    }

    fun sendZontikEndlessStart(start: Boolean) {
        if (started && SettingsManager.getTelegramSendTaziks()) {
            val text = if (start) {
                String.format(
                    "🟢☂️ %.2f%% / %.2f%% / %.2f / v%d / %ds / %ds / %.2f%%",
                    SettingsManager.getZontikEndlessChangePercent(),
                    SettingsManager.getZontikEndlessTakeProfit(),
                    SettingsManager.getZontikEndlessApproximationFactor(),
                    SettingsManager.getZontikEndlessMinVolume(),
                    SettingsManager.getZontikEndlessResetIntervalSeconds(),
                    SettingsManager.getZontikEndlessOrderLifeTimeSeconds(),
                    SettingsManager.getZontikEndlessClosePriceProtectionPercent()
                )
            } else {
                "🔴☂️ стоп!"
            }
            sendMessageToChats(text, 15)
        }
    }

    fun sendTazikBuy(
        purchase: StockPurchase,
        buyPrice: Double,
        sellPrice: Double,
        priceFrom: Double,
        priceTo: Double,
        change: Double,
        tazikUsed: Int,
        tazikTotal: Int
    ) {
        if (started && SettingsManager.getTelegramSendTaziks()) {
            val marker = if (purchase.broker == BrokerType.TINKOFF) "🟡" else "🔵"

            val text = "🛁%s $%s B%.2f$ -> S%.2f$, F%.2f$ -> T%.2f$ = %.2f%%, %d/%d".format(
                locale = Locale.US,
                marker,
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

    fun sendZontikSell(
        purchase: StockPurchase,
        sellPrice: Double,
        buyPrice: Double,
        priceFrom: Double,
        priceTo: Double,
        change: Double,
        tazikUsed: Int,
        tazikTotal: Int
    ) {
        if (started && SettingsManager.getTelegramSendTaziks()) {
            val marker = if (purchase.broker == BrokerType.TINKOFF) "🟡" else "🔵"

            val text = "☂️%s $%s S%.2f$ -> B%.2f$, F%.2f$ -> T%.2f$ = %.2f%%, %d/%d".format(
                locale = Locale.US,
                marker,
                purchase.ticker,
                sellPrice,
                buyPrice,
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

    fun sendTazikSpike(purchase: StockPurchase, buyPrice: Double, priceFrom: Double, priceTo: Double, change: Double, tazikUsed: Int, tazikTotal: Int) {
        if (started && SettingsManager.getTelegramSendSpikes()) {
            val marker = if (purchase.broker == BrokerType.TINKOFF) "🟡" else "🔵"

            val text = "спайк! 🛁%s $%s B%.2f$, F%.2f$ -> T%.2f$ = %.2f%%, %d/%d".format(
                locale = Locale.US,
                marker,
                purchase.ticker,
                buyPrice,
                priceFrom,
                priceTo,
                change,
                tazikUsed,
                tazikTotal
            )
            val buttons = getButtonsMarkup(purchase.stock)
            sendMessageToChats(text, deleteAfterSeconds = 15, replyMarkup = buttons)
        }
    }

    fun sendZontikSpike(
        purchase: StockPurchase,
        buyPrice: Double,
        priceFrom: Double,
        priceTo: Double,
        change: Double,
        tazikUsed: Int,
        tazikTotal: Int
    ) {
        if (started && SettingsManager.getTelegramSendSpikes()) {
            val marker = if (purchase.broker == BrokerType.TINKOFF) "🟡" else "🔵"

            val text = "спайк! ☂️%s $%s B%.2f$, F%.2f$ -> T%.2f$ = %.2f%%, %d/%d".format(
                locale = Locale.US,
                marker,
                purchase.ticker,
                buyPrice,
                priceFrom,
                priceTo,
                change,
                tazikUsed,
                tazikTotal
            )
            val buttons = getButtonsMarkup(purchase.stock)
            sendMessageToChats(text, deleteAfterSeconds = 15, replyMarkup = buttons)
        }
    }

    private fun sendMessageToChats(
        text: String,
        deleteAfterSeconds: Int = -1,
        stop: Boolean = false,
        replyMarkup: ReplyMarkup? = null,
        replyToMessageId: Long? = null
    ) {
        GlobalScope.launch(Dispatchers.Default) {
            try {
                val chatIds = SettingsManager.getTelegramChatID()
                for (chatId in chatIds) {
                    if (text == "") break

                    while (true) {
                        val result = telegramBot?.sendMessage(
                            ChatId.fromId(id = chatId),
                            text = text,
                            replyMarkup = replyMarkup,
                            replyToMessageId = replyToMessageId,
                            parseMode = ParseMode.HTML
                        )
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

    private fun getButtonsMarkup(stock: Stock): ReplyMarkup? {
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

    private fun getButtonsMarkupMany(stocks: List<Stock>): ReplyMarkup? {
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
        sendMessageToChats("$${stock.getTickerLove()} ${price}$ / ${change2300}%", deleteAfterSeconds = -1, replyMarkup = buttons)
    }

    fun sendStockInfo(stock: Stock) {
        val stockInfo = stock.stockInfo
        val buttons = getButtonsMarkup(stock)
        if (stockInfo != null) {
            val price = stock.getPriceRaw()
            val percentUp = "%.2f".format(Utils.getPercentFromTo(stockInfo.limit_up, price))
            val percentDown = "%.2f".format(Utils.getPercentFromTo(stockInfo.limit_down, price))
            sendMessageToChats(
                "$${stock.getTickerLove()} ${price}$ - ⬆️${stockInfo.limit_up}$ / ${percentUp}% ⬇️${stockInfo.limit_down}$ / ${percentDown}%",
                deleteAfterSeconds = -1,
                replyMarkup = buttons
            )
        } else {
            sendMessageToChats(
                "$${stock.getTickerLove()} нет лимитов, current = ${stock.getPriceNow()}",
                deleteAfterSeconds = -1,
                replyMarkup = buttons
            )
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
            sendMessageToChats(phrase, deleteAfterSeconds = 120, replyToMessageId = messageId)
        }
    }

    fun sendDepo() {
        val positions = brokerManager.getPositionsAll()
        positions.removeAll { it.getPositionStock() == null }
        val stocks = positions.map { it.getPositionStock()!! }

        var text = "<code>%-7s %-4s %-6s %-6s %-5s\n".format("💼", "lot", "avg.", "$", "%")
        for (p in positions) {
            val marker = if (p is TinkoffPosition) "🟡" else "🔵"

            val profitMoney = if (p.getProfitAmount() > 0) "+%-5.2f".format(p.getProfitAmount()) else "%-6.2f".format(p.getProfitAmount())
            val profitPercent = if (p.getProfitPercent() > 0) "+%-4.2f".format(p.getProfitPercent()) else "%-5.2f".format(p.getProfitPercent())
            text += "%s%-5s %-4d %-6.2f %s %s\n".format(
                marker,
                p.getPositionStock()?.ticker,
                p.getLots(),
                p.getAveragePrice(),
                profitMoney,
                profitPercent
            )
        }
        text += "</code>"

        if (positions.isEmpty()) {
            text = "💼 ✅"
        }

        val buttons = getButtonsMarkupMany(stocks)
        sendMessageToChats(text, deleteAfterSeconds = -1, replyMarkup = buttons)
    }

    fun sendTop(stocks: List<Stock>, count: Int, top: Boolean) {
        val min = min(count, stocks.size)
        var text = if (top) "TOP 🟢\n" else "BOT 🔴\n"
        for (i in 0 until min) {
            val stock = stocks[i]
            text += "<b>%4.2f%%</b> $%s %4.2f$ -> %4.2f$ \n".format(
                stock.changePrice2300DayPercent,
                stock.getTickerLove(),
                stock.getPrice2300(),
                stock.getPriceRaw()
            )
        }
        val buttons = getButtonsMarkupMany(stocks.subList(0, min))
        sendMessageToChats(text, deleteAfterSeconds = -1, replyMarkup = buttons)
    }

    fun sendDayLow(stocks: List<Stock>, count: Int) {
        val min = min(count, stocks.size)
        var text = "DAY LOW 🔴\n"
        for (i in 0 until min) {
            val stock = stocks[i]
            text += "<b>%4.2f%%</b> ⤴️ %.2f%% $%s %4.2f$ -> %4.2f$\n".format(
                stock.changePrice2300DayPercent,
                stock.changePriceLowDayPercent,
                stock.getTickerLove(),
                stock.getPrice2300(),
                stock.getPriceRaw()
            )
        }
        val buttons = getButtonsMarkupMany(stocks.subList(0, min))
        sendMessageToChats(text, deleteAfterSeconds = -1, replyMarkup = buttons)
    }

    fun sendDayHigh(stocks: List<Stock>, count: Int) {
        val min = min(count, stocks.size)
        var text = "DAY HIGH 🟢\n"
        for (i in 0 until min) {
            val stock = stocks[i]
            text += "<b>%4.2f%%</> ⤵️ %.2f%% $%s %4.2f$ -> %4.2f$\n".format(
                stock.changePrice2300DayPercent,
                stock.changePriceHighDayPercent,
                stock.getTickerLove(),
                stock.getPrice2300(),
                stock.getPriceRaw()
            )
        }
        val buttons = getButtonsMarkupMany(stocks.subList(0, min))
        sendMessageToChats(text, deleteAfterSeconds = -1, replyMarkup = buttons)
    }

    fun sendArb(long: Boolean, stocks: List<Stock>, count: Int) {
        val min = min(count, stocks.size)
        var text = if (long) "🏴‍☠️ LONG 🟢\n" else "🏴‍☠️ SHORT 🔴\n"

        for (i in 0 until min) {
            val stock = stocks[i]
            val change = if (long) stock.changePriceArbLongPercent else stock.changePriceArbShortPercent
            val emoji = when {
                change >=  7.0 -> "7️⃣"
                change >=  5.0 -> "5️⃣"
                change >=  3.0 -> "3️⃣"
                change >=  2.0 -> "2️⃣"
                change >=  1.0 -> "1️⃣"
                change >   0.0 -> "0️⃣"
                else -> ""
            }
            val changePercent = if (change > 0) {
                "+%.2f%%".format(locale = Locale.US, change)
            } else {
                "%.2f%%".format(locale = Locale.US, change)
            }
            text += "$emoji <b>$changePercent</b> $${stock.getTickerLove()} "
            if (long) {
                val sum = stock.askPriceRU * stock.askLotsRU
                text += "${stock.askPriceRU.toMoney(stock)} -> ${stock.getPrice2300().toMoney(stock)} ⨊ = <b>${sum.toMoney(stock)}</b>"
            } else {
                val sum = stock.bidPriceRU * stock.bidLotsRU
                text += "${stock.bidPriceRU.toMoney(stock)} -> ${stock.getPrice2300().toMoney(stock)} ⨊ = <b>${sum.toMoney(stock)}</b>"
            }

            text += "\n"
        }

        val buttons = getButtonsMarkupMany(stocks.subList(0, min))
        sendMessageToChats(text, deleteAfterSeconds = -1, replyMarkup = buttons)
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