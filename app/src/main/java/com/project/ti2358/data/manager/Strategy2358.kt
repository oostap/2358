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
    private val depoManager: DepoManager by inject()
    private val ordersService: OrdersService by inject()

    var stocks: MutableList<Stock> = mutableListOf()
    var stocksSelected: MutableList<Stock> = mutableListOf()
    var stocksToPurchase: MutableList<PurchaseStock> = mutableListOf()

    public fun process() : MutableList<Stock> {
        val all = stockManager.stocksStream
        stocks.clear()

        val change = SettingsManager.get2358ChangePercent()
        val volumeDayPieces = SettingsManager.get2358VolumeDayPieces()
        val volumeDayCash = SettingsManager.get2358VolumeDayCash()

        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        for (stock in all) {
            if (stock.changePriceDayPercent <= change &&            // изменение
                stock.todayDayCandle.volume >= volumeDayPieces &&   // объём в шт
                stock.dayVolumeCash >= volumeDayCash &&             // объём в $
                stock.todayDayCandle.openingPrice > min &&          // мин цена
                stock.todayDayCandle.openingPrice < max) {          // макс цена
                stocks.add(stock)
            }
        }
        stocks.sortBy { it.changePriceDayPercent }
        return stocks
    }

    public fun setSelected(stock: Stock, value : Boolean) {
        if (value) {
            stocksSelected.remove(stock)
        } else {
            if (!stocksSelected.contains(stock))
                stocksSelected.add(stock)
        }
        stocksSelected.sortBy { it.changePriceDayPercent }
    }

    public fun isSelected(stock: Stock) : Boolean {
        return stocksSelected.contains(stock)
    }

    public fun getPurchaseStock(prepare: Boolean) : MutableList<PurchaseStock> {
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
                if (delta > 1.5) {
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
        var onePiece : Double = totalMoney / stocksToPurchase.size

        for (purchase in stocksToPurchase) {
            purchase.lots = (onePiece / purchase.stock.todayDayCandle.closingPrice).roundToInt()
            purchase.status = PurchaseStatus.BEFORE_BUY

            if (prepare) { // запоминаем % подготовки, чтобы после проверить изменение
                purchase.stock.changeOnStartTimer = purchase.stock.changePriceDayPercent
            }
        }

        return stocksToPurchase
    }

    public fun getTotalPurchaseString() : String {
        var value = 0.0
        for (stock in stocksToPurchase) {
            value += stock.lots * stock.stock.todayDayCandle.closingPrice
        }
        return "%.1f".format(value) + "$"
    }

    public fun getTotalPurchasePieces() : Int {
        var value = 0
        for (stock in stocksToPurchase) {
            value += stock.lots
        }
        return value
    }

    public fun getNotificationTextShort(): String {
        var price = getTotalPurchaseString()
        var tickers = ""
        for (stock in stocksToPurchase) {
            tickers += "${stock.lots}*${stock.stock.marketInstrument.ticker} "
        }

        return "$price:\n$tickers"
    }

    public fun getNotificationTextLong(): String {
        var tickers = ""
        for (stock in stocksToPurchase) {
            val p = "%.1f".format(stock.lots * stock.stock.todayDayCandle.closingPrice) + "$"
            tickers += "${stock.stock.marketInstrument.ticker} * ${stock.lots} шт. = ${p} ${stock.getStatusString()}\n"
        }

        return tickers
    }
}