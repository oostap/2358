package com.project.ti2358.data.manager

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.project.ti2358.R
import com.project.ti2358.TheApplication
import com.project.ti2358.data.model.dto.daager.PresetStock
import com.project.ti2358.service.Sorting
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.lang.Exception
import java.util.*
import kotlin.math.roundToInt

@KoinApiExtension
class Strategy1000Buy : KoinComponent {
    private val stockManager: StockManager by inject()

    var stocks: MutableList<Stock> = mutableListOf()
    var presetStocksSelected: MutableList<PresetStock> = mutableListOf()
    var toBuyPurchase: MutableList<StockPurchase> = mutableListOf()
    var currentSort: Sorting = Sorting.DESCENDING

    var stocksToBuy700: MutableList<StockPurchase> = mutableListOf()
    var stocksToBuy1000: MutableList<StockPurchase> = mutableListOf()

    var started700: Boolean = false
    var started1000: Boolean = false

    var job1000: MutableList<Job?> = mutableListOf()
    var job700: MutableList<Job?> = mutableListOf()

    var currentNumberSet: Int = 0

    suspend fun process(numberSet: Int) = withContext(StockManager.stockContext) {
        val all = stockManager.getWhiteStocks()
        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        stocks = all.filter { (it.getPriceNow() > min && it.getPriceNow() < max) || it.getPriceNow() == 0.0 || it.getPrice2300() == 0.0 }.toMutableList()
        stocks.sortBy { it.changePrice2300DayPercent }
        loadSelectedStocks(numberSet)
    }

    private fun loadSelectedStocks(numberSet: Int) {
        presetStocksSelected = SettingsManager.get1000BuySet(numberSet).toMutableList()
    }

    public fun saveSelectedStocks(numberSet: Int = currentNumberSet) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(TheApplication.application.applicationContext)
        val editor: SharedPreferences.Editor = preferences.edit()

        val key = when (numberSet) {
            1 -> TheApplication.application.applicationContext.getString(R.string.setting_key_1000_buy_set_1)
            2 -> TheApplication.application.applicationContext.getString(R.string.setting_key_1000_buy_set_2)
            3 -> TheApplication.application.applicationContext.getString(R.string.setting_key_1000_buy_set_3)
            4 -> TheApplication.application.applicationContext.getString(R.string.setting_key_1000_buy_set_4)
            else -> ""
        }

        // сохранить лоты и проценты из PurchaseStock
        for (purchase in toBuyPurchase) {
            val preset = presetStocksSelected.find { it.ticker == purchase.ticker}
            preset?.let {
                it.percent = purchase.percentLimitPriceChange
                it.lots = purchase.lots
            }
        }

        if (key != "") {
            var data = ""
            for (preset in presetStocksSelected) {
                data += "%s %.2f %d\n".format(locale = Locale.US, preset.ticker, preset.percent, preset.lots)
            }
            editor.putString(key, data)
            editor.apply()
        }
    }

    fun resort(): MutableList<Stock> {
        currentSort = if (currentSort == Sorting.DESCENDING) Sorting.ASCENDING else Sorting.DESCENDING
        stocks.sortBy { stock ->
            val sign = if (currentSort == Sorting.ASCENDING) 1 else -1
            val multiplier3 = if (presetStocksSelected.find { it.ticker == stock.ticker } != null) 1000 else 1
            stock.changePrice2300DayPercent * sign - multiplier3
        }
        return stocks
    }

    fun setSelected(stock: Stock, value: Boolean, numberSet: Int) {
        if (value) {
            if (presetStocksSelected.find { it.ticker == stock.ticker } == null) {
                presetStocksSelected.add(PresetStock(stock.ticker, -1.0, 0))
            }
        } else {
            presetStocksSelected.removeAll { it.ticker == stock.ticker }
        }
        saveSelectedStocks(numberSet)
    }

    fun isSelected(stock: Stock): Boolean {
        return presetStocksSelected.find { it.ticker == stock.ticker } != null
    }

    fun getPurchaseStock(): MutableList<StockPurchase> {
        val totalMoney: Double = SettingsManager.get1000BuyPurchaseVolume().toDouble()
        val onePiece: Double = totalMoney / presetStocksSelected.size

        val purchases: MutableList<StockPurchase> = mutableListOf()
        for (preset in presetStocksSelected) {
            val stock = stocks.find { it.ticker == preset.ticker }
            if (stock != null) {
                val purchase = StockPurchase(stock)

                // из настроек
                purchase.percentLimitPriceChange = preset.percent
                purchase.lots = preset.lots

                // уже заданные
                for (p in toBuyPurchase) {
                    if (p.ticker == stock.ticker) {
                        purchase.apply {
                            percentLimitPriceChange = p.percentLimitPriceChange
                            lots = p.lots
                        }
                        break
                    }
                }
                purchases.add(purchase)
            }
        }
        toBuyPurchase = purchases

        toBuyPurchase.forEach {
            if (it.percentLimitPriceChange == 0.0) {
                it.percentLimitPriceChange = -1.0
            }
            it.updateAbsolutePrice()
            if (it.lots == 0) { // если уже настраивали количество, то не трогаем
                if (!it.getLimitPriceDouble().isNaN()) {
                    it.lots = (onePiece / it.getLimitPriceDouble()).roundToInt()
                }
            }
            it.status = PurchaseStatus.WAITING
        }

        return toBuyPurchase
    }

    fun getTotalPurchaseString(purchases: MutableList<StockPurchase>): String {
        var value = 0.0
        for (p in purchases) {
            value += p.lots * p.getLimitPriceDouble()
        }
        return "%.1f$".format(locale = Locale.US, value)
    }

    fun getTotalPurchasePieces(): Int {
        var value = 0
        for (stock in toBuyPurchase) {
            value += stock.lots
        }
        return value
    }

    fun getNotificationTextShort(purchases: MutableList<StockPurchase>): String {
        val price = getTotalPurchaseString(purchases)
        var tickers = ""
        for (p in purchases) {
            tickers += "${p.lots}*${p.ticker} "
        }

        return "$price:\n$tickers"
    }

    fun getNotificationTextLong(purchases: MutableList<StockPurchase>): String {
        var tickers = ""
        for (p in purchases) {
            val text = "%.1f$ > %.2f$ > %.2f%%".format(locale = Locale.US,
                p.lots * p.getLimitPriceDouble(),
                p.getLimitPriceDouble(),
                p.percentLimitPriceChange
            )
            tickers += "${p.ticker} * ${p.lots} = $text ${p.getStatusString()}\n"
        }

        return tickers
    }

    fun prepareBuy700() {
        started700 = false
        stocksToBuy700 = toBuyPurchase
    }

    fun prepareBuy1000() {
        started1000 = false
        stocksToBuy1000 = toBuyPurchase
    }

    fun startStrategy700Buy() {
        if (started700) return
        started700 = true

        for (purchase in stocksToBuy700) {
            val job = purchase.buyLimitFromBid(purchase.getLimitPriceDouble(), SettingsManager.get1000BuyTakeProfit(), 50, SettingsManager.get1000BuyOrderLifeTimeSeconds())
            if (job != null)
                job700.add(job)
        }
    }

    fun startStrategy1000Buy() {
        if (started1000) return
        started1000 = true

        for (purchase in stocksToBuy1000) {
            val job = purchase.buyLimitFromBid(purchase.getLimitPriceDouble(), SettingsManager.get1000BuyTakeProfit(), 50, SettingsManager.get1000BuyOrderLifeTimeSeconds())
            if (job != null)
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