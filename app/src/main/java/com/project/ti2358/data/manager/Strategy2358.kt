package com.project.ti2358.data.service

import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.project.ti2358.data.manager.PurchaseStatus
import com.project.ti2358.data.manager.PurchaseStock
import com.project.ti2358.data.model.dto.*
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.kotlin.subscribeBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.roundToInt
import kotlin.random.Random

@KoinApiExtension
class Strategy2358() : KoinComponent {
    private val stockManager: StockManager by inject()

    var stocks: MutableList<Stock> = mutableListOf()
    var stocksSelected: MutableList<Stock> = mutableListOf()
    var stocksToPurchase: MutableList<PurchaseStock> = mutableListOf()

    fun process() : MutableList<Stock> {
        val all = stockManager.stocksStream
        stocks.clear()

        val change = SettingsManager.get2358ChangePercent()
        val volumeDayPieces = SettingsManager.get2358VolumeDayPieces()
        val volumeDayCash = SettingsManager.get2358VolumeDayCash() * 1000 * 1000

        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        for (stock in all) {
            if (stock.changePriceDayPercent <= change &&            // изменение
                stock.getTodayVolume() >= volumeDayPieces &&        // объём в шт
                stock.dayVolumeCash >= volumeDayCash &&             // объём в $
                stock.getPriceDouble() > min &&          // мин цена
                stock.getPriceDouble() < max) {          // макс цена
                stocks.add(stock)
            }
        }
        stocks.sortBy { it.changePriceDayPercent }
        return stocks
    }

    fun setSelected(stock: Stock, value : Boolean) {
        if (value) {
            stocksSelected.remove(stock)
        } else {
            if (!stocksSelected.contains(stock))
                stocksSelected.add(stock)
        }
        stocksSelected.sortBy { it.changePriceDayPercent }
    }

    fun isSelected(stock: Stock) : Boolean {
        return stocksSelected.contains(stock)
    }

    fun getPurchaseStock(prepare: Boolean) : MutableList<PurchaseStock> {
        // проверить и удалить бумаги, которые перестали удовлетворять условию 2358
        process()
        stocksSelected.removeAll { !stocks.contains(it) }

        // проверить и удалить бумаги, которые сильно отросли с момента старта таймера
        if (!prepare) {
            var stocksToDelete: MutableList<Stock> = mutableListOf()
            for (stock in stocksSelected) {
                if (stock.changeOnStartTimer == 0.0) continue

                var delta = stock.changeOnStartTimer / stock.changePriceDayPercent

                // если бумага отросла больше, чем на половину, то отменить покупку
                if (delta >= 1.4) {
                    stocksToDelete.add(stock)
                }
            }

            stocksSelected.removeAll { stocksToDelete.contains(it) }
        }

        stocksToPurchase.clear()
        for (stock in stocksSelected) {
            stocksToPurchase.add(PurchaseStock(stock))
        }

        val totalMoney : Double = SettingsManager.get2358PurchaseVolume().toDouble()
        val onePiece : Double = totalMoney / stocksToPurchase.size

        for (purchase in stocksToPurchase) {
            purchase.lots = (onePiece / purchase.stock.getPriceDouble()).roundToInt()
            purchase.status = PurchaseStatus.WAITING

            if (prepare) { // запоминаем % подготовки, чтобы после проверить изменение
                purchase.stock.changeOnStartTimer = purchase.stock.changePriceDayPercent
            }
        }

        return stocksToPurchase
    }

    fun getTotalPurchaseString() : String {
        var value = 0.0
        for (stock in stocksToPurchase) {
            value += stock.lots * stock.stock.getPriceDouble()
        }
        return "%.1f".format(value) + "$"
    }

    fun getTotalPurchasePieces() : Int {
        var value = 0
        for (stock in stocksToPurchase) {
            value += stock.lots
        }
        return value
    }

    fun getNotificationTextShort(): String {
        val price = getTotalPurchaseString()
        var tickers = ""
        for (stock in stocksToPurchase) {
            tickers += "${stock.lots}*${stock.stock.marketInstrument.ticker} "
        }

        return "$price:\n$tickers"
    }

    fun getNotificationTextLong(): String {
        var tickers = ""
        for (stock in stocksToPurchase) {
            val p = "%.1f".format(stock.lots * stock.stock.getPriceDouble()) + "$"
            tickers += "${stock.stock.marketInstrument.ticker} * ${stock.lots} шт. = ${p} ${stock.getStatusString()}\n"
        }

        return tickers
    }
}