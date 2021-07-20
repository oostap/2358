package com.project.ti2358.ui.portfolio

import android.os.Bundle
import android.view.View
import android.view.View.*
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import com.project.ti2358.R
import com.project.ti2358.data.common.BrokerType
import com.project.ti2358.data.manager.*
import com.project.ti2358.data.tinkoff.model.OperationType
import com.project.ti2358.data.tinkoff.model.TinkoffPosition
import com.project.ti2358.databinding.FragmentPortfolioPositionBinding
import com.project.ti2358.service.Utils
import com.project.ti2358.service.toMoney
import com.project.ti2358.service.toPercent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.util.*
import kotlin.math.abs
import kotlin.math.sign

// TODO: этот экран не поддерживает ALOR

@KoinApiExtension
class PortfolioPositionFragment : Fragment(R.layout.fragment_portfolio_position) {
    private val brokerManager: BrokerManager by inject()
    private val tinkoffPortfolioManager: TinkoffPortfolioManager by inject()
    private val positionManager: PositionManager by inject()
    private val orderbookManager: OrderbookManager by inject()
    private val strategyTrailingStop: StrategyTrailingStop by inject()

    private var fragmentPortfolioPositionBinding: FragmentPortfolioPositionBinding? = null

    private lateinit var stock: Stock
    private lateinit var positionTinkoff: TinkoffPosition
    private lateinit var stockPurchase: StockPurchase

    private var jobOrderbook: Job? = null
    private var jobTrailingStops: MutableList<Job> = mutableListOf()

    override fun onDestroy() {
        fragmentPortfolioPositionBinding = null
        jobOrderbook?.cancel()
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentPortfolioPositionBinding.bind(view)
        fragmentPortfolioPositionBinding = binding

        positionTinkoff = positionManager.activePositionTinkoff!!
        positionTinkoff.stock?.let {
            stock = it

            stockPurchase = StockPurchase(it, BrokerType.TINKOFF).apply {
                position = positionTinkoff
                lots = position?.getLots() ?: 0
                percentProfitSellFrom = Utils.getPercentFromTo(stock.getPriceNow(), position?.getAveragePrice() ?: 0.0)
                trailingStopTakeProfitPercentActivation = SettingsManager.getTrailingStopTakeProfitPercentActivation()
                trailingStopTakeProfitPercentDelta = SettingsManager.getTrailingStopTakeProfitPercentDelta()
                trailingStopStopLossPercent = SettingsManager.getTrailingStopStopLossPercent()
            }
        }

        with(binding) {
            updateButton.setOnClickListener {
                updateOrderbook()
                updateTrailingStop()
                updateLots()
            }

            lotsPlusButton.setOnClickListener {
                stockPurchase.position?.let {
                    stockPurchase.lots += 1
                    if (stockPurchase.lots > it.getLots()) {
                        stockPurchase.lots = it.getLots()
                    }
                }
                updateLots()
            }

            lotsMinusButton.setOnClickListener {
                stockPurchase.position.let {
                    stockPurchase.lots -= 1
                    if (stockPurchase.lots < 1) {
                        stockPurchase.lots = 1
                    }
                }
                updateLots()
            }
            lotsEditText.setOnEditorActionListener { v, actionId, event ->
                val lots = try {
                    Integer.parseInt(v.text.toString())
                } catch (e: Exception) {
                    0
                }
                stockPurchase.lots = lots
                updateLots()
                true
            }

            lotsBar.max = stockPurchase.lots
            lotsBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                    stockPurchase.lots = i
                    updateLots()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            percentPlusButton.setOnClickListener {
                stockPurchase.percentProfitSellFrom += 0.05
                updatePrice()
            }
            percentMinusButton.setOnClickListener {
                stockPurchase.percentProfitSellFrom -= 0.05
                updatePrice()
            }

            ttActivationEdit.setOnEditorActionListener { v, actionId, event ->
                val value = try {
                    (v.text.toString()).toDouble()
                } catch (e: Exception) {
                    1.0
                }
                stockPurchase.trailingStopTakeProfitPercentActivation = value
                updateTrailingStop()
                true
            }
            ttActivationPlusButton.setOnClickListener {
                stockPurchase.trailingStopTakeProfitPercentActivation += 0.05
                updateTrailingStop()
            }
            ttActivationMinusButton.setOnClickListener {
                stockPurchase.trailingStopTakeProfitPercentActivation -= 0.05
                updateTrailingStop()
            }

            ttDeltaEdit.setOnEditorActionListener { v, actionId, event ->
                val value = try {
                    (v.text.toString()).toDouble()
                } catch (e: Exception) {
                    0.25
                }
                stockPurchase.trailingStopTakeProfitPercentDelta = value
                updateTrailingStop()
                true
            }
            ttDeltaPlusButton.setOnClickListener {
                stockPurchase.trailingStopTakeProfitPercentDelta += 0.05
                updateTrailingStop()
            }
            ttDeltaMinusButton.setOnClickListener {
                stockPurchase.trailingStopTakeProfitPercentDelta -= 0.05
                updateTrailingStop()
            }

            ttStopLossEdit.setOnEditorActionListener { v, actionId, event ->
                val value = try {
                    (v.text.toString()).toDouble()
                } catch (e: Exception) {
                    -1.0
                }
                stockPurchase.trailingStopStopLossPercent = value
                updateTrailingStop()
                true
            }
            ttStopLossPlusButton.setOnClickListener {
                stockPurchase.trailingStopStopLossPercent += 0.05
                updateTrailingStop()
            }
            ttStopLossMinusButton.setOnClickListener {
                stockPurchase.trailingStopStopLossPercent -= 0.05
                updateTrailingStop()
            }

            //////////////////////////////////////////////////////////////////////////////////////
            sellAskButton.setOnClickListener {
                stockPurchase.sellToBestAsk()
                updateOrderbook()
            }
            sellBidButton.setOnClickListener {
                stockPurchase.sellToBestBid()
                updateOrderbook()
            }
            sellLimitButton.setOnClickListener {
                stockPurchase.sellWithLimit()
                updateOrderbook()
            }
            sellTtButton.setOnClickListener {
                stockPurchase.trailingStop = true
                stockPurchase.sellWithTrailing()?.let {
                    jobTrailingStops.add(it)
                }
            }

            cancelAllButton.setOnClickListener {
                // отменить все лимитки на продажу
                GlobalScope.launch(Dispatchers.Main) {

                    val orders = tinkoffPortfolioManager.getOrderAllForStock(stock, OperationType.SELL)
                    orders.forEach {
                        brokerManager.cancelOrderTinkoff(it)
                    }
                }

                jobTrailingStops.forEach {
                    try {
                        if (it.isActive) {
                            it.cancel()
                        }
                    } catch (e: java.lang.Exception) {

                    }
                }
                jobTrailingStops.clear()

                strategyTrailingStop.stopTrailingStopsForStock(stock)
            }
        }
        updateTrailingStop()
        updateLots()
        updateOrderbook()
        updatePosition()
    }

    private fun updateTrailingStop() {
        fragmentPortfolioPositionBinding?.apply {
            ttActivationEdit.setText("%.2f".format(locale = Locale.US, stockPurchase.trailingStopTakeProfitPercentActivation))
            ttDeltaEdit.setText("%.2f".format(locale = Locale.US, stockPurchase.trailingStopTakeProfitPercentDelta))
            ttStopLossEdit.setText("%.2f".format(locale = Locale.US, stockPurchase.trailingStopStopLossPercent))

            val avg = stock.getPriceNow()
            val activationPrice = avg + stockPurchase.trailingStopTakeProfitPercentActivation / 100.0 * avg
            val stopLossPrice = avg - abs(stockPurchase.trailingStopStopLossPercent / 100.0 * avg)
            val delta = stockPurchase.trailingStopTakeProfitPercentDelta / 100.0 * avg

            ttActivationPriceView.text = activationPrice.toMoney(stock)
            ttDeltaPriceView.text = "~${delta.toMoney(stock)}"
            ttStopLossPriceView.text = stopLossPrice.toMoney(stock)

            ttActivationPriceView.setTextColor(Utils.GREEN)
            ttDeltaPriceView.setTextColor(Utils.PURPLE)
            ttStopLossPriceView.setTextColor(Utils.RED)
        }
    }

    private fun updatePrice() {
        fragmentPortfolioPositionBinding?.apply {
            val percent = "%.2f".format(locale = Locale.US, stockPurchase.percentProfitSellFrom)
            percentLimitEditText.setText(percent)
            priceLimitView.text = stockPurchase.getProfitPriceForSell().toMoney(stock)

            val positionMoney = stockPurchase.lots * (stockPurchase.position?.getAveragePrice() ?: 0.0)
            val profitMoney = stockPurchase.lots * stockPurchase.getProfitPriceForSell()
            val profitDelta = profitMoney - positionMoney
            profitLimitAbsoluteView.text = profitDelta.toMoney(stock)

            priceLimitView.setTextColor(Utils.getColorForValue(stockPurchase.percentProfitSellFrom))
            profitLimitAbsoluteView.setTextColor(Utils.getColorForValue(stockPurchase.percentProfitSellFrom))
        }
    }

    private fun updateLots() {
        fragmentPortfolioPositionBinding?.apply {
            lotsEditText.setText(stockPurchase.lots.toString(), TextView.BufferType.EDITABLE)
            lotsPercentView.text = "? %"
            stockPurchase.position?.let {
                lotsPercentView.text = (stockPurchase.lots.toDouble() / it.getLots().toDouble() * 100.0).toInt().toString() + "%"
            }
            lotsBar.progress = stockPurchase.lots
        }
        updatePrice()
    }

    private fun updateOrderbook() {
        jobOrderbook?.cancel()
        jobOrderbook = GlobalScope.launch(Dispatchers.Main) {
            try {
                tinkoffPortfolioManager.refreshDeposit()

                val pos = tinkoffPortfolioManager.getPositionForStock(stock)
                if (pos == null) view?.findNavController()?.navigateUp()

                positionTinkoff = pos!!

                val orderbook = positionManager.loadOrderbook()
                orderbook?.let {
                    val priceAsk = it.getBestPriceFromAsk(0)
                    val priceBid = it.getBestPriceFromBid(0)
                    val pricePosition = positionTinkoff.getAveragePrice()

                    val percentBid = Utils.getPercentFromTo(priceBid, pricePosition)
                    val percentAsk = Utils.getPercentFromTo(priceAsk, pricePosition)

                    fragmentPortfolioPositionBinding?.apply {
                        priceAskView.text = priceAsk.toMoney(stock)
                        priceBidView.text = priceBid.toMoney(stock)

                        profitAskView.text = percentAsk.toPercent()
                        profitBidView.text = percentBid.toPercent()

                        val positionMoney = stockPurchase.lots * (stockPurchase.position?.getAveragePrice() ?: 0.0)

                        val profitAskMoney = stockPurchase.lots * priceAsk
                        val profitAskDelta = profitAskMoney - positionMoney
                        profitAskAbsoluteView.text = profitAskDelta.toMoney(stock)

                        val profitBidMoney = stockPurchase.lots * priceBid
                        val profitBidDelta = profitBidMoney - positionMoney
                        profitBidAbsoluteView.text = profitBidDelta.toMoney(stock)

                        profitAskView.setTextColor(Utils.getColorForValue(percentAsk))
                        priceAskView.setTextColor(Utils.getColorForValue(percentAsk))
                        profitAskAbsoluteView.setTextColor(Utils.getColorForValue(percentAsk))

                        profitBidView.setTextColor(Utils.getColorForValue(percentBid))
                        priceBidView.setTextColor(Utils.getColorForValue(percentBid))
                        profitBidAbsoluteView.setTextColor(Utils.getColorForValue(percentBid))
                    }
                }
                updatePosition()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updatePosition() {
        fragmentPortfolioPositionBinding?.apply {
            positionTinkoff.let {
                tickerView.text = "${it.stock?.getTickerLove()}"

                lotsBlockedView.text = if (it.blocked.toInt() > 0) "(${it.blocked.toInt()}🔒)" else ""
                lotsView.text = "${it.getLots()}"

                val avg = it.getAveragePrice()
                priceView.text = "${avg.toMoney(it.stock)} ➡ ${it.stock?.getPriceString()}"

                val profit = it.getProfitAmount()
                priceChangeAbsoluteView.text = profit.toMoney(it.stock)

                val percent = it.getProfitPercent() * sign(it.getLots().toDouble()) // инвертировать доходность шорта

                val totalCash = it.balance * avg + profit
                cashView.text = totalCash.toMoney(it.stock)

                val emoji = Utils.getEmojiForPercent(percent)
                priceChangePercentView.text = percent.toPercent() + emoji

                priceView.setTextColor(Utils.getColorForValue(percent))
                priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(percent))
                priceChangePercentView.setTextColor(Utils.getColorForValue(percent))
                orderbookButton.setOnClickListener { _ ->
                    it.stock?.let { stock ->
                        Utils.openOrderbookForStock(orderbookButton.findNavController(), orderbookManager, stock)
                    }
                }

                it.stock?.let { stock ->
                    sectorView.text = stock.getSectorName()
                    sectorView.setTextColor(Utils.getColorForSector(stock.closePrices?.sector))

                    if (stock.report != null) {
                        reportInfoView.text = stock.getReportInfo()
                        reportInfoView.visibility = VISIBLE
                    } else {
                        reportInfoView.visibility = GONE
                    }
                    reportInfoView.setTextColor(Utils.RED)
                }
            }
        }
    }
}