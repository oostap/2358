package com.project.ti2358.data.manager

import com.project.ti2358.TheApplication
import com.project.ti2358.data.common.BrokerType
import com.project.ti2358.data.tinkoff.model.Currency
import com.project.ti2358.service.*
import kotlinx.coroutines.Job
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.lang.Exception
import java.util.*
import kotlin.math.roundToInt

@KoinApiExtension
class Strategy2225() : KoinComponent {
    private val stockManager: StockManager by inject()
    private val portfolioManager: PortfolioManager by inject()
    private val alorPortfolioManager: AlorPortfolioManager by inject()

    private val strategyTelegram: StrategyTelegram by inject()

    var stocks: MutableList<Stock> = mutableListOf()
    var stocksSelected: MutableList<Stock> = mutableListOf()
    var stocksToPurchase: MutableList<StockPurchase> = mutableListOf()
    var jobs: MutableList<Job?> = mutableListOf()
    var started: Boolean = false

    var equalParts = true

    fun process(): MutableList<Stock> {
        val all = stockManager.getWhiteStocks()
        val change = SettingsManager.get2225ChangePercent()
        val volumeDayPieces = SettingsManager.get2225VolumeDayPieces()
        val volumeDayCash = SettingsManager.get2225VolumeDayCash() * 1000 * 1000
        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        stocks = all.filter { stock ->
            stock.changePrice2300DayPercent >= change &&    // изменение
            stock.getTodayVolume() >= volumeDayPieces &&    // объём в шт
            stock.dayVolumeCash >= volumeDayCash &&         // объём в $
            stock.getPriceNow() > min &&                   // мин цена
            stock.getPriceNow() < max &&                   // макс цена
            stock.short != null
        }.toMutableList()

        stocks.sortByDescending {
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

    private fun preparePurchase(purchase: StockPurchase) {
        purchase.apply {
            val p = stocksToPurchase.find { it.ticker == purchase.ticker && it.broker == purchase.broker }
            if (p != null) {
                percentProfitSellFrom = p.percentProfitSellFrom
                percentProfitSellTo = p.percentProfitSellTo
                lots = p.lots
            } else {
                percentProfitSellFrom = SettingsManager.get2225TakeProfitFrom()
                percentProfitSellTo = SettingsManager.get2225TakeProfitTo()
            }
        }
    }

    fun getPurchaseStock(reset: Boolean): MutableList<StockPurchase> {
        process()

        if (reset) started = false

        // удалить бумаги, которые перестали удовлетворять условию 2358
        stocksSelected.removeAll { it !in stocks }

        val purchases: MutableList<StockPurchase> = mutableListOf()
        for (stock in stocksSelected) {
            if (SettingsManager.getBrokerTinkoff()) {
                val purchase = StockPurchaseTinkoff(stock)
                preparePurchase(purchase)
                purchases.add(purchase)
            }

            if (SettingsManager.getBrokerAlor()) {
                val purchase = StockPurchaseAlor(stock)
                preparePurchase(purchase)
                purchases.add(purchase)
            }
        }
        stocksToPurchase = purchases

        // удалить все бумаги, которые уже есть в портфеле, чтобы избежать коллизий
        if (SettingsManager.getTazikEndlessExcludeDepo()) {
            stocksToPurchase.removeAll { p -> portfolioManager.portfolioPositions.any { it.ticker == p.ticker && p.broker == BrokerType.TINKOFF } }
            stocksToPurchase.removeAll { p -> alorPortfolioManager.portfolioPositions.any { it.symbol == p.ticker && p.broker == BrokerType.ALOR } }
        }

        val allTinkoff = stocksToPurchase.filter { it.broker == BrokerType.TINKOFF }.size
        val allAlor = stocksToPurchase.filter { it.broker == BrokerType.ALOR }.size

        val totalMoneyTinkoff: Double = SettingsManager.get2225PurchaseVolume().toDouble()
        val onePieceTinkoff: Double = if (allTinkoff == 0) 0.0 else totalMoneyTinkoff / allTinkoff

        val totalMoneyAlor: Double = SettingsManager.get2225PurchaseVolume().toDouble() * SettingsManager.getAlorMultiplierMoney()
        val onePieceAlor: Double = if (allAlor == 0) 0.0 else totalMoneyAlor / allAlor

        stocksToPurchase.forEach {
            if (it.lots == 0 || equalParts) { // если уже настраивали количество, то не трогаем
                val part = when (it.broker) {
                    BrokerType.TINKOFF -> if (it.stock.instrument.currency == Currency.RUB) onePieceTinkoff * Utils.getUSDRUB() else onePieceTinkoff
                    BrokerType.ALOR -> if (it.stock.instrument.currency == Currency.RUB) onePieceAlor * Utils.getUSDRUB() else onePieceAlor
                }

                it.lots = (part / it.stock.getPriceNow()).roundToInt()
            }
            it.status = PurchaseStatus.WAITING
        }

        return stocksToPurchase
    }

    fun getTotalPurchaseString(): String {
        var value = 0.0
        stocksToPurchase.forEach { value += it.lots * it.stock.getPriceNow() }
        return value.toMoney(null)
    }

    fun getTotalPurchasePieces(): Int {
        var value = 0
        stocksToPurchase.forEach { value += it.lots }
        return value
    }

    fun getNotificationTextShort(): String {
        var tickers = ""
        stocksToPurchase.forEach { tickers += "${it.lots}*${it.ticker} " }
        return "${getTotalPurchaseString()}:\n$tickers"
    }

    fun getNotificationTextLong(): String {
        var tickers = ""
        for (purchase in stocksToPurchase) {
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
        strategyTelegram.send2225Start(true, stocksToPurchase.map { it.ticker })
        Utils.startService(TheApplication.application.applicationContext, Strategy2225Service::class.java)
    }

    fun stopStrategyCommand() {
        Utils.stopService(TheApplication.application.applicationContext, Strategy2225Service::class.java)
    }

    fun startStrategy() {
        if (started) return
        started = true

        val localPurchases = getPurchaseStock(false)
        localPurchases.forEach {
            jobs.add(it.sellShortToBid2225())
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

        strategyTelegram.send2225Start(false, stocksToPurchase.map { it.ticker })
    }
}