package com.project.ti2358.data.manager

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.project.ti2358.R
import com.project.ti2358.TheApplication
import com.project.ti2358.data.common.BrokerType
import com.project.ti2358.data.daager.model.PresetStock
import com.project.ti2358.data.tinkoff.model.TinkoffPosition
import com.project.ti2358.service.PurchaseStatus
import com.project.ti2358.service.Sorting
import com.project.ti2358.service.toMoney
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.lang.Exception
import java.util.*
import kotlin.math.abs

@KoinApiExtension
class Strategy1000Sell() : KoinComponent {
    private val stockManager: StockManager by inject()
    private val brokerManager: BrokerManager by inject()

    var stocks: MutableList<Stock> = mutableListOf()

    var presetStocksSelected: MutableList<PresetStock> = mutableListOf()

    // первая вкладка - позиции из двух депозитов
    private var purchaseFromPortfolio: MutableList<StockPurchase> = mutableListOf()

    // выбранные позиции для продажи
    var purchaseFromPortfolioSelected: MutableList<StockPurchase> = mutableListOf()

    private var stocksToPurchase: MutableList<StockPurchase> = mutableListOf()

    var positionsToSell700: MutableList<StockPurchase> = mutableListOf()
    var positionsToSell1000: MutableList<StockPurchase> = mutableListOf()
    var currentSort: Sorting = Sorting.DESCENDING

    var job1000: MutableList<Job?> = mutableListOf()
    var job700: MutableList<Job?> = mutableListOf()

    var started700: Boolean = false
    var started1000: Boolean = false

    var currentNumberSet: Int = 0

    suspend fun processShort(numberSet: Int) = withContext(StockManager.stockContext) {
        val all = stockManager.getWhiteStocks()
        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        stocks = all.filter { (it.getPriceNow() > min && it.getPriceNow() < max) || it.getPriceNow() == 0.0 || it.getPrice2300() == 0.0  }.toMutableList()
        stocks.sortBy { it.changePrice2300DayPercent }

        // удалить все бумаги, по которым нет шорта в ТИ
        stocks.removeAll { it.short == null}

        loadSelectedStocks(numberSet)
    }

    suspend fun processPortfolio(): List<StockPurchase> = withContext(StockManager.stockContext) {
        val positions = brokerManager.getPositionsAll()
        purchaseFromPortfolio.clear()
        positions.forEach { pos ->
            if (pos.getLots() > 0) {
                pos.getPositionStock()?.let { stock ->
                    val broker = if (pos is TinkoffPosition) BrokerType.TINKOFF else BrokerType.ALOR
                    val p = StockPurchase(stock, broker)
                    p.position = pos
                    p.lots = pos.getLots()
                    purchaseFromPortfolio.add(p)
                }
            }
        }
        return@withContext purchaseFromPortfolio
    }

    private fun loadSelectedStocks(numberSet: Int) {
        presetStocksSelected = SettingsManager.get1000SellSet(numberSet).toMutableList()
    }

    fun saveSelectedStocks(numberSet: Int = currentNumberSet) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(TheApplication.application.applicationContext)
        val editor: SharedPreferences.Editor = preferences.edit()

        val key = when (numberSet) {
            1 -> TheApplication.application.applicationContext.getString(R.string.setting_key_1000_sell_set_1)
            2 -> TheApplication.application.applicationContext.getString(R.string.setting_key_1000_sell_set_2)
            3 -> TheApplication.application.applicationContext.getString(R.string.setting_key_1000_sell_set_3)
            4 -> TheApplication.application.applicationContext.getString(R.string.setting_key_1000_sell_set_4)
            else -> ""
        }

        // сохранить лоты и проценты из PurchaseStock
        for (purchase in stocksToPurchase) {
            val preset = presetStocksSelected.find { it.ticker == purchase.ticker}
            preset?.let {
                it.percent = purchase.percentLimitPriceChange
                it.lots = purchase.lots
                it.profit = purchase.profitPercent
            }
        }

        if (key != "") {
            var data = ""
            for (preset in presetStocksSelected) {
                if (preset.ticker != "") {
                    data += "%s %.2f %d %.2f\n".format(locale = Locale.US, preset.ticker, preset.percent, preset.lots, preset.profit)
                }
            }
            editor.putString(key, data)
            editor.apply()
        }
    }

    suspend fun resort(): MutableList<Stock> = withContext(StockManager.stockContext) {
        currentSort = if (currentSort == Sorting.DESCENDING) Sorting.ASCENDING else Sorting.DESCENDING
        stocks.sortBy { stock ->
            val sign = if (currentSort == Sorting.ASCENDING) 1 else -1
            val multiplier3 = if (presetStocksSelected.find { it.ticker == stock.ticker } != null) 1000 else 1
            stock.changePrice2300DayPercent * sign - multiplier3
        }
        return@withContext stocks
    }

    suspend fun setSelectedShort(stock: Stock, value: Boolean, numberSet: Int) = withContext(StockManager.stockContext) {
        if (value) {
            if (presetStocksSelected.find { it.ticker == stock.ticker } == null) {
                presetStocksSelected.add(PresetStock(stock.ticker, 1.0, 0, 0.0))
            }
        } else {
            presetStocksSelected.removeAll { it.ticker == stock.ticker }
        }
        saveSelectedStocks(numberSet)
    }

    suspend fun setSelectedPortfolio(purchase: StockPurchase, value: Boolean) = withContext(StockManager.stockContext) {
        if (value) {
            if (purchaseFromPortfolioSelected.find { it.ticker == purchase.ticker && it.broker == purchase.broker } == null) {
                purchaseFromPortfolioSelected.add(purchase)
            } else {

            }
        } else {
            purchaseFromPortfolioSelected.removeAll { it.ticker == purchase.ticker && it.broker == purchase.broker }
        }
    }

    fun isSelectedPortfolio(purchase: StockPurchase): Boolean {
        return purchaseFromPortfolioSelected.find { it.ticker == purchase.ticker } != null
    }

    fun isSelectedShort(stock: Stock): Boolean {
        return presetStocksSelected.find { it.ticker == stock.ticker } != null
    }

    fun processPrepare(): MutableList<StockPurchase> {
        if (currentNumberSet != 0) loadSelectedStocks(currentNumberSet)

        val purchases: MutableList<StockPurchase> = mutableListOf()

        if (currentNumberSet != 0) {
            for (preset in presetStocksSelected) {
                stocks.find { it.ticker == preset.ticker }?.let {
                    if (SettingsManager.getBrokerTinkoff()) {
                        val purchase = StockPurchase(it, BrokerType.TINKOFF).apply {
                            percentLimitPriceChange = preset.percent
                            lots = preset.lots
                            profitPercent = preset.profit
                        }
                        purchases.add(purchase)
                    }

                    if (SettingsManager.getBrokerAlor()) {
                        val purchase = StockPurchase(it, BrokerType.ALOR).apply {
                            percentLimitPriceChange = preset.percent
                            lots = preset.lots
                            profitPercent = preset.profit
                        }
                        purchases.add(purchase)
                    }
                }
            }
        } else { // продажа депозита
            purchaseFromPortfolioSelected.forEach {
                purchases.add(it)
            }
        }
        stocksToPurchase = purchases

        stocksToPurchase.forEach {
            if (it.percentLimitPriceChange == 0.0) it.processInitialProfit()
            if (it.profitPercent == 0.0) it.profitPercent = SettingsManager.get1000SellTakeProfitBuy()
            if (it.lots == 0) it.lots = it.position?.getLots() ?: 1

            it.updateAbsolutePrice()

            it.status = PurchaseStatus.WAITING
        }

        stocksToPurchase.removeAll { it.lots == 0 }
        return stocksToPurchase
    }

    fun getTotalPurchasePieces(): Int {
        stocksToPurchase.removeAll { it.lots == 0 }

        var value = 0
        for (purchaseStock in stocksToPurchase) {
            value += purchaseStock.lots
        }
        return value
    }

    fun getTotalSellString(): String {
        var value = 0.0
        for (purchaseStock in stocksToPurchase) {
            value += purchaseStock.getLimitPriceDouble() * purchaseStock.lots
        }
        return value.toMoney(null)
    }

    fun getTotalSellString(purchases: MutableList<StockPurchase>): String {
        var value = 0.0
        for (purchaseStock in purchases) {
            value += purchaseStock.getLimitPriceDouble() * purchaseStock.lots
        }
        return value.toMoney(null)
    }

    fun getNotificationTextShort(purchases: MutableList<StockPurchase>): String {
        val price = getTotalSellString(purchases)
        var tickers = ""
        for (purchaseStock in purchases) {
            tickers += "${purchaseStock.lots}*${purchaseStock.ticker} "
        }

        return "$price:\n$tickers"
    }

    fun getNotificationTextLong(purchases: MutableList<StockPurchase>): String {
        var tickers = ""
        for (p in purchases) {
            val text = "%.1f$ > %.2f$ > %.2f%% > ТП%.2f%%".format(locale = Locale.US,
                p.lots * p.getLimitPriceDouble(),
                p.getLimitPriceDouble(),
                p.percentLimitPriceChange,
                p.profitPercent
            )
            tickers += "${p.ticker}*${p.lots} = $text ${p.getStatusString()}\n"
        }
        return tickers.trim()
    }

    fun prepareSell700() {
        started700 = false
        positionsToSell700 = stocksToPurchase
    }

    fun prepareSell1000() {
        started1000 = false
        positionsToSell1000 = stocksToPurchase
    }

    fun startStrategy700Sell() {
        if (started700) return
        started700 = true

        for (purchase in positionsToSell700) {
            // если позиция уже в портфеле, то НЕ выставлять ТП после продажи позиции
            val profit = if (purchase.position == null) purchase.profitPercent else 0.0

            // время жизни заявки для позиции из депо и для шорта разные
            val lifetimeSell = if (purchase.position == null) SettingsManager.get1000SellOrderLifeTimeSecondsShort() else SettingsManager.get1000SellOrderLifeTimeSecondsDepo()

            // время жизни заявки на откуп шорта
            val lifetimeBuy = if (purchase.position == null) SettingsManager.get1000SellOrderLifeTimeSecondsBuy() else 0

            val job = purchase.sellLimitFromAsk(purchase.getLimitPriceDouble(), profit, 50, lifetimeSell, lifetimeBuy)
            if (job != null) job700.add(job)
        }
    }

    fun startStrategyNow() {
        for (purchase in stocksToPurchase) {
            // если позиция уже в портфеле, то НЕ выставлять ТП после продажи позиции
            val profit = if (purchase.position == null) purchase.profitPercent else 0.0

            // время жизни заявки для позиции из депо и для шорта разные
            val lifetimeShort = if (purchase.position == null) SettingsManager.get1000SellOrderLifeTimeSecondsShort() else 0

            // время жизни заявки на откуп шорта
            val lifetimeBuy = if (purchase.position == null) SettingsManager.get1000SellOrderLifeTimeSecondsBuy() else 0

            val job = purchase.sellLimitFromAsk(purchase.getLimitPriceDouble(), profit, 50, lifetimeShort, lifetimeBuy)
            if (job != null) job1000.add(job)
        }
    }

    fun startStrategy1000Sell() {
        if (started1000) return
        started1000 = true

        for (purchase in positionsToSell1000) {
            // если позиция уже в портфеле, то НЕ выставлять ТП после продажи позиции
            val profit = if (purchase.position == null) purchase.profitPercent else 0.0

            // время жизни заявки для позиции из депо и для шорта разные
            val lifetimeShort = if (purchase.position == null) SettingsManager.get1000SellOrderLifeTimeSecondsShort() else 0

            // время жизни заявки на откуп шорта
            val lifetimeBuy = if (purchase.position == null) SettingsManager.get1000SellOrderLifeTimeSecondsBuy() else 0

            val job = purchase.sellLimitFromAsk(purchase.getLimitPriceDouble(), profit, 50, lifetimeShort, lifetimeBuy)
            if (job != null) job1000.add(job)
        }
    }

    fun stopStrategy700() {
        job700.forEach {
            try {
                if (it?.isActive == true) {
                    it.cancel()
                }
            } catch (e: Exception) {

            }
        }
        job700.clear()
    }

    fun stopStrategy1000() {
        job1000.forEach {
            try {
                if (it?.isActive == true) {
                    it.cancel()
                }
            } catch (e: Exception) {

            }
        }
        job1000.clear()
    }
}