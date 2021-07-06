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
class StrategyDayLow : KoinComponent {
    private val stockManager: StockManager by inject()
    private val portfolioManager: PortfolioManager by inject()
    private val strategyTelegram: StrategyTelegram by inject()

    var stocks: MutableList<Stock> = mutableListOf()
    var stocksSelected: MutableList<Stock> = mutableListOf()
    var toBuyPurchase: MutableList<StockPurchase> = mutableListOf()
    var jobs: MutableList<Job?> = mutableListOf()
    var started: Boolean = false

    var equalParts = true

    fun process(): MutableList<Stock> {
//        val all = stockManager.getWhiteStocks()
        val all = stockManager.stocksStream
        val changeFromLow = 2.0       //SettingsManager.get2358ChangePercent()
        val changeDay = -1.0       //SettingsManager.get2358ChangePercent()
        val volumeDayPieces = 0 //SettingsManager.get2358VolumeDayPieces()
        val volumeDayCash = 0   //SettingsManager.get2358VolumeDayCash() * 1000 * 1000
        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        stocks = all.filter { stock ->
            stock.changePriceLowDayPercent <= changeFromLow && // изменение с лоя
            stock.changePrice2300DayPercent <= changeDay &&    // изменение дня
            stock.getTodayVolume() >= volumeDayPieces &&    // объём в шт
            stock.dayVolumeCash >= volumeDayCash &&         // объём в $
            stock.getPriceNow() > min &&                 // мин цена
            stock.getPriceNow() < max                    // макс цена
        }.toMutableList()

        stocks.sortBy {
            val multiplier = if (it in stocksSelected) 100 else 1
            (it.changePriceLowDayPercent + it.changePrice2300DayPercent) * multiplier
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
        stocksSelected.sortBy { it.changePriceLowDayPercent }
    }

    fun isSelected(stock: Stock): Boolean {
        return stock in stocksSelected
    }

    fun getPurchaseStock(reset: Boolean): MutableList<StockPurchase> {
        process()

        if (reset) started = false

        // удалить бумаги, которые уже есть в депо, иначе среднюю невозможно узнать
        stocksSelected.removeAll { stock ->
            portfolioManager.portfolioPositions.any { it.ticker == stock.ticker }
        }

        val purchases: MutableList<StockPurchase> = mutableListOf()
        for (stock in stocksSelected) {
            val purchase = StockPurchase(stock)

            var exists = false
            for (p in toBuyPurchase) {
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
        toBuyPurchase = purchases

        val totalMoney: Double = SettingsManager.get2358PurchaseVolume().toDouble()
        val onePiece: Double = totalMoney / toBuyPurchase.size

        for (purchase in toBuyPurchase) {
            if (purchase.lots == 0 || equalParts) { // если уже настраивали количество, то не трогаем
                purchase.lots = (onePiece / purchase.stock.getPriceNow()).roundToInt()
            }
            purchase.status = PurchaseStatus.WAITING

            if (reset) { // запоминаем % подготовки, чтобы после проверить изменение
                purchase.stock.changeOnStartTimer = purchase.stock.changePrice2300DayPercent
            }
        }

        return toBuyPurchase
    }

    fun getTotalPurchaseString(): String {
        var value = 0.0
        toBuyPurchase.forEach { value += it.lots * it.stock.getPriceNow() }
        return value.toMoney(null)
    }

    fun getTotalPurchasePieces(): Int {
        var value = 0
        toBuyPurchase.forEach { value += it.lots }
        return value
    }

    fun getNotificationTextShort(): String {
        var tickers = ""
        toBuyPurchase.forEach { tickers += "${it.lots}*${it.ticker} " }
        return "${getTotalPurchaseString()}:\n$tickers"
    }

    fun getNotificationTextLong(): String {
        var tickers = ""
        for (purchase in toBuyPurchase) {
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
        strategyTelegram.send2358Start(true, toBuyPurchase.map { it.ticker })
        Utils.startService(TheApplication.application.applicationContext, StrategyDayLowService::class.java)
    }

    fun stopStrategyCommand() {
        Utils.stopService(TheApplication.application.applicationContext, StrategyDayLowService::class.java)
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

        strategyTelegram.send2358DayLowStart(false, toBuyPurchase.map { it.ticker })
    }
}