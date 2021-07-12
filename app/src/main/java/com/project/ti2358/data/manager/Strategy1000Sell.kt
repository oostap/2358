package com.project.ti2358.data.manager

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.project.ti2358.R
import com.project.ti2358.TheApplication
import com.project.ti2358.data.daager.model.PresetStock
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
    private val portfolioManager: PortfolioManager by inject()
    private val stockManager: StockManager by inject()

    var stocks: MutableList<Stock> = mutableListOf()
    var presetStocksSelected: MutableList<PresetStock> = mutableListOf()

    private var toSellPurchase: MutableList<StockPurchase> = mutableListOf()

    var positionsToSell700: MutableList<StockPurchase> = mutableListOf()
    var positionsToSell1000: MutableList<StockPurchase> = mutableListOf()
    var currentSort: Sorting = Sorting.DESCENDING

    var job1000: MutableList<Job?> = mutableListOf()
    var job700: MutableList<Job?> = mutableListOf()

    var started700: Boolean = false
    var started1000: Boolean = false

    var currentNumberSet: Int = 0

    suspend fun process(numberSet: Int) = withContext(StockManager.stockContext) {
        val all = stockManager.getWhiteStocks()
        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        stocks = all.filter { (it.getPriceNow() > min && it.getPriceNow() < max) || it.getPriceNow() == 0.0 || it.getPrice2300() == 0.0  }.toMutableList()
        stocks.sortBy { it.changePrice2300DayPercent }

        // удалить все бумаги, по которым нет шорта в ТИ
        stocks.removeAll { it.short == null && portfolioManager.getPositions().find { p -> p.ticker == it.ticker } == null }

        loadSelectedStocks(numberSet)
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
        for (purchase in toSellPurchase) {
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
                data += "%s %.2f %d %.2f\n".format(locale = Locale.US, preset.ticker, preset.percent, preset.lots, preset.profit)
            }
            editor.putString(key, data)
            editor.apply()
        }
    }

    suspend fun resort(): MutableList<Stock> = withContext(StockManager.stockContext) {
        currentSort = if (currentSort == Sorting.DESCENDING) Sorting.ASCENDING else Sorting.DESCENDING
        stocks.sortBy { stock ->
            val sign = if (currentSort == Sorting.ASCENDING) 1 else -1
            val position = portfolioManager.getPositions().find { it.ticker == stock.ticker }
            val multiplier1 = if (position != null) (abs(position.lots * position.getAveragePrice())).toInt() else 1
            val multiplier3 = if (presetStocksSelected.find { it.ticker == stock.ticker } != null) 1000 else 1
            stock.changePrice2300DayPercent * sign - multiplier1 - multiplier3
        }
        return@withContext stocks
    }

    suspend fun setSelected(stock: Stock, value: Boolean, numberSet: Int) = withContext(StockManager.stockContext) {
        if (value) {
            if (presetStocksSelected.find { it.ticker == stock.ticker } == null) {
                presetStocksSelected.add(PresetStock(stock.ticker, 1.0, 0))
            }
        } else {
            presetStocksSelected.removeAll { it.ticker == stock.ticker }
        }
        saveSelectedStocks(numberSet)
    }

    fun isSelected(stock: Stock): Boolean {
        return presetStocksSelected.find { it.ticker == stock.ticker } != null
    }

    fun processPrepare(): MutableList<StockPurchase> {
        loadSelectedStocks(currentNumberSet)

        val purchases: MutableList<StockPurchase> = mutableListOf()

        for (preset in presetStocksSelected) {
            val stock = stocks.find { it.ticker == preset.ticker }
            if (stock != null) {
                val purchase = StockPurchase(stock)

                purchase.percentLimitPriceChange = preset.percent
                purchase.lots = preset.lots
                purchase.profitPercent = preset.profit

                purchase.apply {
                    position = portfolioManager.getPositionForFigi(stock.figi)
                }
                purchases.add(purchase)
            }
        }
        toSellPurchase = purchases

        toSellPurchase.forEach {
            if (it.percentLimitPriceChange == 0.0) it.processInitialProfit()
            if (it.profitPercent == 0.0) it.profitPercent = SettingsManager.get1000SellTakeProfit()
            if (it.lots == 0) it.lots = it.position?.lots ?: 1

            it.updateAbsolutePrice()

            it.status = PurchaseStatus.WAITING
        }
        return toSellPurchase
    }

    fun getTotalPurchasePieces(): Int {
        var value = 0
        for (purchaseStock in toSellPurchase) {
            value += purchaseStock.lots
        }
        return value
    }

    fun getTotalSellString(): String {
        var value = 0.0
        for (purchaseStock in toSellPurchase) {
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
        positionsToSell700 = toSellPurchase
    }

    fun prepareSell1000() {
        started1000 = false
        positionsToSell1000 = toSellPurchase
    }

    fun startStrategy700Sell() {
        if (started700) return
        started700 = true

        for (purchase in positionsToSell700) {
            // если позиция уже в портфеле, то НЕ выставлять ТП, а просто добрать шорт или продать позицию
            val profit = if (purchase.position == null) purchase.profitPercent else 0.0
            val job = purchase.sellLimitFromAsk(purchase.getLimitPriceDouble(), profit, 50, SettingsManager.get1000SellOrderLifeTimeSeconds())
            job700.add(job)
        }
    }

    fun startStrategy1000Sell() {
        if (started1000) return
        started1000 = true

        for (purchase in positionsToSell1000) {
            // если позиция уже в портфеле, то НЕ выставлять ТП, а просто добрать шорт или продать позицию
            val profit = if (purchase.position == null) purchase.profitPercent else 0.0
            val job = purchase.sellLimitFromAsk(purchase.getLimitPriceDouble(), profit, 50, SettingsManager.get1000SellOrderLifeTimeSeconds())
            job1000.add(job)
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