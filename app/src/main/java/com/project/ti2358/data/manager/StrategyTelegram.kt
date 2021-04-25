package com.project.ti2358.data.manager

import android.annotation.SuppressLint
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.*
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.network.fold

import com.project.ti2358.data.model.dto.Operation
import com.project.ti2358.data.model.dto.OperationStatus
import com.project.ti2358.data.model.dto.OperationType
import com.project.ti2358.data.service.OperationsService
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
    private val depositManager: DepositManager by inject()
    private val strategyFollower: StrategyFollower by inject()

    var jobUpdateOperations: Job? = null
    var operations: MutableList<Operation> = mutableListOf()
    var operationsPosted: MutableList<String> = mutableListOf()

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

                    depositManager.refreshDeposit()

                    operations = Collections.synchronizedList(operationsService.operations(from, to, depositManager.getActiveBrokerAccountId()).operations)
                    operations.sortBy { it.date }
                    if (operationsPosted.isEmpty()) {
                        operations.forEach {
                            operationsPosted.add(it.id)
                        }
                    }

                    for (operation in operations) {
                        if (operation.id !in operationsPosted) {
                            operationsPosted.add(operation.id)
                            if (operation.status != OperationStatus.DONE || operation.price == 0.0 || operation.quantity == 0) continue

                            operation.stock = stockManager.getStockByFigi(operation.figi)

                            val dateNow = Calendar.getInstance()
                            val dateOperation = Calendar.getInstance()
                            dateOperation.time = operation.date
                            if (abs(dateOperation.get(Calendar.DAY_OF_YEAR) - dateNow.get(Calendar.DAY_OF_YEAR)) > 1) {
                                continue
                            }

                            try {
                                val chatId = SettingsManager.getTelegramChatID().toLong()
                                while (true) {
                                    val result = telegramBot?.sendMessage(ChatId.fromId(id = chatId), text = operationToString(operation))
                                    if (result?.first?.isSuccessful == true) {
                                        break
                                    } else {
                                        delay(5000)
                                        continue
                                    }
                                }
                            } catch (e: Exception) {

                            }
                            continue
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
                        val success = strategyFollower.processStrategy(update.message!!.from?.id ?: 0, command)
                        val status = if (success) "+" else "-"
                        bot.sendMessage(ChatId.fromId(id = update.message!!.chat.id), text = status, replyToMessageId = update.message!!.messageId)
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

        GlobalScope.launch(Dispatchers.Default) {
            try {
                val chatId = SettingsManager.getTelegramChatID().toLong()
                val result = telegramBot?.sendMessage(ChatId.fromId(id = chatId), text = SettingsManager.getTelegramHello())
                log(result.toString())
            } catch (e: Exception) {

            }
        }
    }

    fun stopStrategy() {
        started = false
        GlobalScope.launch(Dispatchers.Default) {
            try {
                val chatId = SettingsManager.getTelegramChatID().toLong()
                telegramBot?.sendMessage(ChatId.fromId(id = chatId), text = SettingsManager.getTelegramBye())
                telegramBot?.stopPolling()
                jobUpdateOperations?.cancel()
            } catch (e: Exception) {

            }
        }
    }

    private fun convertDateToTinkoffDate(calendar: Calendar, zone: String): String {
        return calendar.time.toString("yyyy-MM-dd'T'HH:mm:ss.SSSSSS") + zone
    }

    @SuppressLint("SimpleDateFormat")
    private fun operationToString(operation: Operation): String {
        val ticker = operation.stock?.ticker
        var operationString = if (operation.operationType == OperationType.BUY) "Покупка 🟢 " else "Продажа 🔴 "
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

        val dateString = SimpleDateFormat("dd.MM.yyyy HH:mm:ss.SSS").format(operation.date)
        var depo = ""
        position?.let {
            val percent = it.getProfitPercent() * sign(it.lots.toDouble())
            val emoji = Utils.getEmojiForPercent(percent)
            depo += "\n💼: %d шт. * %.2f$ = %.2f > %.2f%%%s".format(it.lots, it.getAveragePrice(), it.lots * it.getAveragePrice(), percent, emoji)

        }
        return "$%s %s\n%d шт. * %.2f$ = %.2f\n%s%s".format(ticker, operationString, operation.quantityExecuted, operation.price, operation.quantityExecuted * operation.price, dateString, depo)
    }

    fun sendRocket(rocketStock: RocketStock) {
        if (started && SettingsManager.getTelegramSendRockets()) {
            GlobalScope.launch(Dispatchers.Default) {
                try {
                    val emoji = if (rocketStock.changePercent > 0) "🚀" else "☄️"
                    val changePercent = if (rocketStock.changePercent > 0) {
                        "+%.2f%%".format(locale = Locale.US, rocketStock.changePercent)
                    } else {
                        "%.2f%%".format(locale = Locale.US, rocketStock.changePercent)
                    }
                    val text = "$emoji$${rocketStock.ticker} ${rocketStock.priceFrom.toMoney(rocketStock.stock)} -> ${rocketStock.priceTo.toMoney(rocketStock.stock)} = $changePercent за ${rocketStock.time} мин, v = ${rocketStock.volume}"
                    val chatId = SettingsManager.getTelegramChatID().toLong()
                    val result = telegramBot?.sendMessage(ChatId.fromId(id = chatId), text = text)
                } catch (e: Exception) {

                }
            }
        }
    }
}