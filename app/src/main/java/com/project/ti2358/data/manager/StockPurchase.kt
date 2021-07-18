package com.project.ti2358.data.manager

import com.project.ti2358.data.common.BasePosition
import com.project.ti2358.data.common.BrokerType
import com.project.ti2358.service.PurchaseStatus
import com.project.ti2358.service.Utils
import kotlinx.coroutines.Job
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import java.util.*

/// TODO: !!!!! Различия StockPurchaseTinkoff и StockPurchaseAlor только в способах подтверждения установки заявки, в будущем нужно их объединить
@KoinApiExtension
open class StockPurchase(open var stock: Stock, open var broker: BrokerType) : KoinComponent {
    open lateinit var ticker: String
    open lateinit var figi: String

    var position: BasePosition? = null

    var tazikPrice: Double = 0.0                      // обновляемая фиксированная цена, от которой считаем тазы
    var tazikEndlessPrice: Double = 0.0               // обновляемая фиксированная цена, от которой считаем бесконечные тазы
    var zontikEndlessPrice: Double = 0.0              // обновляемая фиксированная цена, от которой считаем бесконечные зонты

    var fixedPrice: Double = 0.0                      // зафиксированная цена, от которой шагаем лимитками
    var percentLimitPriceChange: Double = 0.0         // разница в % с текущей ценой для создания лимитки
    var absoluteLimitPriceChange: Double = 0.0        // если лимитка, то по какой цене
    var lots: Int = 0                                 // сколько штук тарим / продаём
    var profitPercent: Double = 0.0                   // процент профита лонг/шорт (> 0.0)

    var status: PurchaseStatus = PurchaseStatus.NONE

    // для продажи/откупа лесенкой в 2225 и 2258 и DayLOW
    var percentProfitSellFrom: Double = 0.0
    var percentProfitSellTo: Double = 0.0

    var currentTrailingStop: TrailingStop? = null
    var trailingStop: Boolean = false
    var trailingStopTakeProfitPercentActivation: Double = 0.0
    var trailingStopTakeProfitPercentDelta: Double = 0.0
    var trailingStopStopLossPercent: Double = 0.0

    companion object {
        const val DelaySuperFast: Long = 75
        const val DelayFast: Long = 150
        const val DelayMiddle: Long = 400
        const val DelayLong: Long = 2000
    }

    open fun buyLimitFromBid(price: Double, profit: Double, counter: Int, orderLifeTimeSeconds: Int): Job? { return null }
    open fun sellLimitFromAsk(price: Double, profit: Double, counter: Int, orderLifeTimeSeconds: Int): Job? { return null }
    open fun buyFromAsk1728(): Job? { return null }
    open fun buyFromAsk2358(): Job? { return null }
    open fun sellWithLimit(): Job? { return null }
    open fun sellToBestBid(): Job? { return null }
    open fun sellToBestAsk(): Job? { return null }
    open fun sellWithTrailing(): Job? { return null }
    open fun sellShortToBid2225(): Job? { return null }

    fun getPriceString(): String {
        return "%.1f$".format(locale = Locale.US, fixedPrice * lots)
    }

    fun getStatusString(): String =
        when (status) {
            PurchaseStatus.NONE -> "NONE"
            PurchaseStatus.WAITING -> "⏳"
            PurchaseStatus.ORDER_BUY_PREPARE -> "ордер: до покупки"
            PurchaseStatus.ORDER_BUY -> "ордер: покупка!"
            PurchaseStatus.BOUGHT -> "куплено! 💸"
            PurchaseStatus.ORDER_SELL_TRAILING -> "ТТ 📈"
            PurchaseStatus.ORDER_SELL_PREPARE -> "ордер: до продажи"
            PurchaseStatus.ORDER_SELL -> "ордер: продажа!"
            PurchaseStatus.SOLD -> "продано! 🤑"
            PurchaseStatus.CANCELED -> "отменена! 🛑"
            PurchaseStatus.PART_FILLED -> "частично налили, продаём"
            PurchaseStatus.ERROR_NEED_WATCH -> "ошибка, дальше руками 🤷‍"
        }

    fun getLimitPriceDouble(): Double {
        val buyPrice = fixedPrice + absoluteLimitPriceChange
        return Utils.makeNicePrice(buyPrice, stock)
    }

    fun addLots(lot: Int) {
        lots += lot
        if (lots < 1) lots = 1

        position?.let {
            if (lots > it.getLots() && stock.short == null) { // если бумага недоступна в шорт, то ограничить лоты размером позиции
                lots = it.getLots()
            }
        }
    }

    fun addPriceLimitPercent(change: Double) {
        percentLimitPriceChange += change
        updateAbsolutePrice()
    }

    fun updateAbsolutePrice() {
        fixedPrice = stock.getPriceNow()
        absoluteLimitPriceChange = fixedPrice / 100 * percentLimitPriceChange
        absoluteLimitPriceChange = Utils.makeNicePrice(absoluteLimitPriceChange, stock)
    }

    fun addPriceProfit2358Percent(change: Double) {
        percentProfitSellFrom += change
        percentProfitSellTo += change
    }

    fun addPriceProfit2358TrailingTakeProfit(change: Double) {
        trailingStopTakeProfitPercentActivation += change
        trailingStopTakeProfitPercentDelta += change * 0.4
    }

    fun getProfitPriceForSell(): Double {
        position?.let { // если есть поза, берём среднюю
            val avg = it.getAveragePrice()
            val priceProfit = avg + avg / 100.0 * percentProfitSellFrom
            return Utils.makeNicePrice(priceProfit, stock)
        }

        // иначе берём текущую цену бумаги
        val priceProfit = stock.getPriceNow() + stock.getPriceNow() / 100.0 * percentProfitSellFrom
        return Utils.makeNicePrice(priceProfit, stock)
    }

    fun processInitialProfit() {
        percentLimitPriceChange = SettingsManager.get1000SellTakeProfit()

        position?.let {
            // по умолчанию взять профит из настроек
            var futureProfit = SettingsManager.get1000SellTakeProfit()

            // если не задан в настройках, то 1% по умолчанию
            if (futureProfit == 0.0) futureProfit = 1.0

            // если текущий профит уже больше, то за базовый взять его
            val currentProfit = it.getProfitPercent()

            percentLimitPriceChange = if (currentProfit > futureProfit) currentProfit else futureProfit
        }
        status = PurchaseStatus.WAITING
    }
}
