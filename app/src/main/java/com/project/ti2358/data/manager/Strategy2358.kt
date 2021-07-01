package com.project.ti2358.data.manager

import com.project.ti2358.TheApplication
import com.project.ti2358.service.*
import kotlinx.coroutines.Job
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.lang.Exception
import java.util.*
import kotlin.math.roundToInt

@KoinApiExtension
class Strategy2358() : KoinComponent {
    private val stockManager: StockManager by inject()
    private val depositManager: DepositManager by inject()
    private val strategyTelegram: StrategyTelegram by inject()

    var stocks: MutableList<Stock> = mutableListOf()
    var stocksSelected: MutableList<Stock> = mutableListOf()
    var purchaseToBuy: MutableList<PurchaseStock> = mutableListOf()
    var jobs: MutableList<Job?> = mutableListOf()
    var started: Boolean = false

    var equalParts = true

    fun process(): MutableList<Stock> {
        val all = stockManager.getWhiteStocks()
        val change = SettingsManager.get2358ChangePercent()
        val volumeDayPieces = SettingsManager.get2358VolumeDayPieces()
        val volumeDayCash = SettingsManager.get2358VolumeDayCash() * 1000 * 1000
        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        stocks = all.filter { stock ->
            stock.changePrice2300DayPercent <= change &&    // изменение
            stock.getTodayVolume() >= volumeDayPieces &&    // объём в шт
            stock.dayVolumeCash >= volumeDayCash &&         // объём в $
            stock.getPriceNow() > min &&                 // мин цена
            stock.getPriceNow() < max                    // макс цена
        }.toMutableList()

        stocks.sortBy {
            val multiplier = if (it in stocksSelected) 100 else 1
            it.changePrice2300DayPercent * multiplier
        }

        return stocks
    }

    fun setSelected(stock: Stock, value: Boolean) {
        if (value) {
            if (stock !in stocksSelected)
                stocksSelected.add(stock)
        } else {
            stocksSelected.remove(stock)
        }
        stocksSelected.sortBy { it.changePrice2300DayPercent }
    }

    fun isSelected(stock: Stock): Boolean {
        return stock in stocksSelected
    }

    fun getPurchaseStock(reset: Boolean): MutableList<PurchaseStock> {
        process()

        if (reset) started = false

        if (SettingsManager.get2358ProtectStockUp()) { // защита от откупа
            // удалить бумаги, которые перестали удовлетворять условию 2358
            stocksSelected.removeAll { it !in stocks }

            // проверить и удалить бумаги, которые сильно отросли с момента старта таймера
            if (!reset) {
                val stocksToDelete: MutableList<Stock> = mutableListOf()
                for (stock in stocksSelected) {
                    if (stock.changeOnStartTimer == 0.0) continue

                    val delta = stock.changeOnStartTimer / stock.changePrice2300DayPercent

                    // если бумага отросла больше, чем на половину, то отменить покупку
                    if (delta >= 1.6) {
                        stocksToDelete.add(stock)
                    }
                }

                stocksSelected.removeAll { it in stocksToDelete }
            }
        }

        // удалить бумаги, которые уже есть в депо, иначе среднюю невозможно узнать
        stocksSelected.removeAll { stock ->
            depositManager.portfolioPositions.any { it.ticker == stock.ticker }
        }

        val purchases: MutableList<PurchaseStock> = mutableListOf()
        for (stock in stocksSelected) {
            val purchase = PurchaseStock(stock)

            var exists = false
            for (p in purchaseToBuy) {
                if (p.ticker == stock.ticker) {
                    purchase.apply {
                        percentProfitSellFrom = p.percentProfitSellFrom
                        percentProfitSellTo = p.percentProfitSellTo

                        trailingStop = p.trailingStop
                        trailingStopTakeProfitPercentActivation = p.trailingStopTakeProfitPercentActivation
                        trailingStopTakeProfitPercentDelta = p.trailingStopTakeProfitPercentDelta
                        lots = p.lots
                    }
                    exists = true
                    break
                }
            }

            if (!exists) {
                purchase.apply {
                    percentProfitSellFrom = SettingsManager.get2358TakeProfitFrom()
                    percentProfitSellTo = SettingsManager.get2358TakeProfitTo()

                    trailingStopTakeProfitPercentActivation = SettingsManager.getTrailingStopTakeProfitPercentActivation()
                    trailingStopTakeProfitPercentDelta = SettingsManager.getTrailingStopTakeProfitPercentDelta()
                    trailingStopStopLossPercent = 0.0 // нет стоп-лоссу на 2358!
                }
            }

            purchases.add(purchase)
        }
        purchaseToBuy = purchases

        val totalMoney: Double = SettingsManager.get2358PurchaseVolume().toDouble()
        val onePiece: Double = totalMoney / purchaseToBuy.size

        for (purchase in purchaseToBuy) {
            if (purchase.lots == 0 || equalParts) { // если уже настраивали количество, то не трогаем
                purchase.lots = (onePiece / purchase.stock.getPriceNow()).roundToInt()
            }
            purchase.status = PurchaseStatus.WAITING

            if (reset) { // запоминаем % подготовки, чтобы после проверить изменение
                purchase.stock.changeOnStartTimer = purchase.stock.changePrice2300DayPercent
            }
        }

        return purchaseToBuy
    }

    fun getTotalPurchaseString(): String {
        var value = 0.0
        purchaseToBuy.forEach { value += it.lots * it.stock.getPriceNow() }
        return value.toMoney(null)
    }

    fun getTotalPurchasePieces(): Int {
        var value = 0
        purchaseToBuy.forEach { value += it.lots }
        return value
    }

    fun getNotificationTextShort(): String {
        var tickers = ""
        purchaseToBuy.forEach { tickers += "${it.lots}*${it.ticker} " }
        return "${getTotalPurchaseString()}:\n$tickers"
    }

    fun getNotificationTextLong(): String {
        var tickers = ""
        for (purchase in purchaseToBuy) {
            val p = "%.2f$".format(locale = Locale.US, purchase.lots * purchase.stock.getPriceNow())
            tickers += "${purchase.ticker}*${purchase.lots} = ${p}, "
            tickers += if (purchase.trailingStop) {
                "ТТ:${purchase.trailingStopTakeProfitPercentActivation.toPercent()}/${purchase.trailingStopTakeProfitPercentDelta.toPercent()}, ${purchase.getStatusString()} ${purchase.currentTrailingStop?.currentChangePercent?.toPercent() ?: ""}\n"
            } else {
                "Л:${purchase.percentProfitSellFrom.toPercent()}/${purchase.percentProfitSellTo.toPercent()}, ${purchase.getStatusString()}\n"
            }
        }

        return tickers
    }

    fun prepareStrategyCommand(tickers: List<String>) {
        stocksSelected.clear()
        tickers.forEach {
            val stock = stockManager.getStockByTicker(it)
            if (stock != null) {
                setSelected(stock, true)
            }
        }

        getPurchaseStock(true)
        strategyTelegram.send2358Start(true, purchaseToBuy.map { it.ticker })
        Utils.startService(TheApplication.application.applicationContext, Strategy2358Service::class.java)
    }

    fun stopStrategyCommand() {
        Utils.stopService(TheApplication.application.applicationContext, Strategy2358Service::class.java)
    }

    fun startStrategy() {
        if (started) return
        started = true

        val localPurchases = getPurchaseStock(false)
        localPurchases.forEach {
            jobs.add(it.buyFromAsk2358())
        }
    }

    fun stopStrategy() {
        jobs.forEach {
            try {
                if (it?.isActive == true) {
                    it.cancel()
                }
            } catch (e: Exception) {

            }
        }
        jobs.clear()

        strategyTelegram.send2358Start(false, purchaseToBuy.map { it.ticker })
    }
}