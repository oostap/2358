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
import com.project.ti2358.service.toString
import kotlinx.coroutines.*
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sign

@KoinApiExtension
class StrategyTelegram : KoinComponent {
    private val stockManager: StockManager by inject()
    private val operationsService: OperationsService by inject()
    private val depositManager: DepositManager by inject()

    var jobUpdateOperations: Job? = null
    var operations: MutableList<Operation> = mutableListOf()
    var lastOperationID: String = ""

    var telegramBot: Bot? = null

    private fun startUpdateOperations() {
        val delay = SettingsManager.getTelegramUpdateDelay().toLong()
        jobUpdateOperations?.cancel()
        jobUpdateOperations = GlobalScope.launch(Dispatchers.IO) {
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
                    if (lastOperationID == "") {
                        lastOperationID = operations.last().id
                    }

                    var send = false
                    for (operation in operations) {
                        if (send) {
                            if (operation.status != OperationStatus.DONE || operation.price == 0.0 || operation.quantity == 0) continue

                            operation.stock = stockManager.getStockByFigi(operation.figi)

                            val chatId = SettingsManager.getTelegramChatID()
                            while (true) {
                                val result = telegramBot?.sendMessage(ChatId.fromId(id = chatId), text = operationToString(operation))
                                if (result?.first?.isSuccessful == true) {
                                    break
                                } else {
                                    delay(1000)
                                    continue
                                }
                            }
                            continue
                        }

                        if (operation.id == lastOperationID) {
                            send = true
                        }
                    }

                    lastOperationID = operations.last().id
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(delay * 1000)
            }
        }
    }

    fun startStrategy() {
        startUpdateOperations()

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
                    log("telegram msg ${update.message?.text}")
                    val userText = update.message?.text ?: ""
                    if (userText == "chat_id") {
                        val text = "айди чата: ${update.message!!.chat.id}"
                        bot.sendMessage(ChatId.fromId(id = update.message!!.chat.id), text = text)
                        update.consume()
                    }
                }

                // сообщение в канале
                channel {
                    log("telegram msg ${channelPost.text} ")
                    val userText = channelPost.text ?: ""

                    if (userText == "chat_id") {
                        val text = "айди чата: ${channelPost.chat.id}"
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

        jobUpdateOperations = GlobalScope.launch(Dispatchers.IO) {
            val chatId = SettingsManager.getTelegramChatID()
            telegramBot?.sendMessage(ChatId.fromId(id = chatId), text = SettingsManager.getTelegramHello())
        }
    }

    fun stopStrategy() {
        telegramBot?.stopPolling()
        jobUpdateOperations?.cancel()
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
            operationString += "LONG"
        }

        if (position == null && operation.operationType == OperationType.SELL) {
            operationString += "SHORT"
        }

        if (position != null && operation.operationType == OperationType.SELL) {
            operationString += if (position.lots < 0) { // продажа в шорте
                "SHORT усреднение"
            } else { // продажа в лонге
                "LONG FIX"
            }
        }

        if (position != null && operation.operationType == OperationType.BUY) {
            operationString += if (position.lots < 0) { // покупка в шорте
                "SHORT FIX"
            } else { // покупка в лонге
                "LONG усреднение"
            }
        }

        val dateString = SimpleDateFormat("dd.MM.yyyy HH:mm:ss.SSS").format(operation.date)
        var depo = ""
        position?.let {
            val percent = it.getProfitPercent() * sign(it.lots.toDouble())
            val emoji = Utils.getEmojiForPercent(percent)
            depo += "\n💼: %d шт., ~%.2f$, %.2f%%%s".format(it.lots, it.averagePositionPrice?.value, percent, emoji)

        }
        return "$%s %s\n%d шт. по цене %.2f$\n%s%s".format(ticker, operationString, operation.quantityExecuted, operation.price, dateString, depo)
    }
}