package com.project.ti2358.ui.portfolio

import android.os.Bundle
import android.view.View
import android.view.View.*
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.project.ti2358.R
import com.project.ti2358.data.manager.*
import com.project.ti2358.data.model.dto.OperationType
import com.project.ti2358.data.model.dto.PortfolioPosition
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
import kotlin.math.sign

@KoinApiExtension
class PortfolioPositionFragment : Fragment(R.layout.fragment_portfolio_position) {
    private val depositManager: DepositManager by inject()
    private val positionManager: PositionManager by inject()
    private val orderbookManager: OrderbookManager by inject()
    private val strategyTrailingStop: StrategyTrailingStop by inject()

    private var fragmentPortfolioPositionBinding: FragmentPortfolioPositionBinding? = null

    private lateinit var stock: Stock
    private lateinit var portfolioPosition: PortfolioPosition
    private lateinit var purchaseStock: PurchaseStock

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

        portfolioPosition = positionManager.activePosition!!
        portfolioPosition.stock?.let {
            stock = it
            purchaseStock = PurchaseStock(it).apply {
                position = portfolioPosition
                lots = position.lots
                percentProfitSellFrom = Utils.getPercentFromTo(stock.getPriceDouble(), position.getAveragePrice())
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
                purchaseStock.position.let {
                    purchaseStock.lots += 1
                    if (purchaseStock.lots > it.lots) {
                        purchaseStock.lots = it.lots
                    }
                }
                updateLots()
            }

            lotsMinusButton.setOnClickListener {
                purchaseStock.position.let {
                    purchaseStock.lots -= 1
                    if (purchaseStock.lots < 1) {
                        purchaseStock.lots = 1
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
                purchaseStock.lots = lots
                updateLots()
                true
            }

            lotsBar.max = purchaseStock.lots
            lotsBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                    purchaseStock.lots = i
                    updateLots()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            percentPlusButton.setOnClickListener {
                purchaseStock.percentProfitSellFrom += 0.05
                updatePrice()
            }
            percentMinusButton.setOnClickListener {
                purchaseStock.percentProfitSellFrom -= 0.05
                updatePrice()
            }

            ttActivationEdit.setOnEditorActionListener { v, actionId, event ->
                val value = try {
                    (v.text.toString()).toDouble()
                } catch (e: Exception) {
                    1.0
                }
                purchaseStock.trailingStopTakeProfitPercentActivation = value
                updateTrailingStop()
                true
            }
            ttActivationPlusButton.setOnClickListener {
                purchaseStock.trailingStopTakeProfitPercentActivation += 0.05
                updateTrailingStop()
            }
            ttActivationMinusButton.setOnClickListener {
                purchaseStock.trailingStopTakeProfitPercentActivation -= 0.05
                updateTrailingStop()
            }

            ttDeltaEdit.setOnEditorActionListener { v, actionId, event ->
                val value = try {
                    (v.text.toString()).toDouble()
                } catch (e: Exception) {
                    0.25
                }
                purchaseStock.trailingStopTakeProfitPercentDelta = value
                updateTrailingStop()
                true
            }
            ttDeltaPlusButton.setOnClickListener {
                purchaseStock.trailingStopTakeProfitPercentDelta += 0.05
                updateTrailingStop()
            }
            ttDeltaMinusButton.setOnClickListener {
                purchaseStock.trailingStopTakeProfitPercentDelta -= 0.05
                updateTrailingStop()
            }

            ttStopLossEdit.setOnEditorActionListener { v, actionId, event ->
                val value = try {
                    (v.text.toString()).toDouble()
                } catch (e: Exception) {
                    -1.0
                }
                purchaseStock.trailingStopStopLossPercent = value
                updateTrailingStop()
                true
            }
            ttStopLossPlusButton.setOnClickListener {
                purchaseStock.trailingStopStopLossPercent += 0.05
                updateTrailingStop()
            }
            ttStopLossMinusButton.setOnClickListener {
                purchaseStock.trailingStopStopLossPercent -= 0.05
                updateTrailingStop()
            }

            //////////////////////////////////////////////////////////////////////////////////////
            sellAskButton.setOnClickListener {
                purchaseStock.sellToBestAsk()
            }
            sellBidButton.setOnClickListener {
                purchaseStock.sellToBestBid()
            }
            sellLimitButton.setOnClickListener {
                purchaseStock.sellWithLimit()
            }
            sellTtButton.setOnClickListener {
                purchaseStock.trailingStop = true
                purchaseStock.sellWithTrailing()?.let {
                    jobTrailingStops.add(it)
                }
            }

            cancelAllButton.setOnClickListener {
                // отменить все лимитки на продажу
                GlobalScope.launch(Dispatchers.Main) {
                    val orders = depositManager.getOrderAllOrdersForFigi(portfolioPosition.figi, OperationType.SELL)
                    orders.forEach {
                        depositManager.cancelOrder(it)
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
    }

    private fun updateTrailingStop() {
        fragmentPortfolioPositionBinding?.apply {
            ttActivationEdit.setText("%.2f".format(locale = Locale.US, purchaseStock.trailingStopTakeProfitPercentActivation))
            ttDeltaEdit.setText("%.2f".format(locale = Locale.US, purchaseStock.trailingStopTakeProfitPercentDelta))
            ttStopLossEdit.setText("%.2f".format(locale = Locale.US, purchaseStock.trailingStopStopLossPercent))

            val avg = purchaseStock.position.getAveragePrice()
            val activationPrice = avg + purchaseStock.trailingStopTakeProfitPercentActivation / 100.0 * avg
            val stopLossPrice = avg - purchaseStock.trailingStopStopLossPercent / 100.0 * avg
            val delta = purchaseStock.trailingStopTakeProfitPercentDelta / 100.0 * avg

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
            val percent = "%.2f".format(locale = Locale.US, purchaseStock.percentProfitSellFrom)
            percentLimitEditText.setText(percent)
            priceLimitView.text = purchaseStock.getProfitPriceForSell().toMoney(stock)

            val positionMoney = purchaseStock.lots * purchaseStock.position.getAveragePrice()
            val profitMoney = purchaseStock.lots * purchaseStock.getProfitPriceForSell()
            val profitDelta = profitMoney - positionMoney
            profitLimitAbsoluteView.text = profitDelta.toMoney(stock)

            priceLimitView.setTextColor(Utils.getColorForValue(purchaseStock.percentProfitSellFrom))
            profitLimitAbsoluteView.setTextColor(Utils.getColorForValue(purchaseStock.percentProfitSellFrom))
        }
    }

    private fun updateLots() {
        fragmentPortfolioPositionBinding?.apply {
            lotsEditText.setText(purchaseStock.lots.toString(), TextView.BufferType.EDITABLE)
            lotsPercentView.text = (purchaseStock.lots.toDouble() / purchaseStock.position.lots.toDouble() * 100.0).toInt().toString() + "%"
            lotsBar.progress = purchaseStock.lots
        }
        updatePrice()
    }

    private fun updateOrderbook() {
        jobOrderbook?.cancel()
        jobOrderbook = GlobalScope.launch(Dispatchers.Main) {
            try {
                depositManager.refreshDeposit()

                val pos = depositManager.getPositionForFigi(portfolioPosition.figi)
                if (pos == null) view?.findNavController()?.navigateUp()

                portfolioPosition = pos!!

                val orderbook = positionManager.loadOrderbook()
                orderbook?.let {
                    val priceAsk = it.getBestPriceFromAsk(1)
                    val priceBid = it.getBestPriceFromBid(1)
                    val pricePosition = portfolioPosition.getAveragePrice()

                    val percentBid = Utils.getPercentFromTo(priceBid, pricePosition)
                    val percentAsk = Utils.getPercentFromTo(priceAsk, pricePosition)

                    fragmentPortfolioPositionBinding?.apply {
                        priceAskView.text = priceAsk.toMoney(stock)
                        priceBidView.text = priceBid.toMoney(stock)

                        profitAskView.text = percentAsk.toPercent()
                        profitBidView.text = percentBid.toPercent()

                        val positionMoney = purchaseStock.lots * purchaseStock.position.getAveragePrice()

                        val profitAskMoney = purchaseStock.lots * priceAsk
                        val profitAskDelta = profitAskMoney - positionMoney
                        profitAskAbsoluteView.text = profitAskDelta.toMoney(stock)

                        val profitBidMoney = purchaseStock.lots * priceBid
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
            portfolioPosition.let {
                tickerView.text = "${it.stock?.getTickerLove()}"

                lotsBlockedView.text = if (it.blocked.toInt() > 0) "(${it.blocked.toInt()}🔒)" else ""
                lotsView.text = "${it.lots}"

                val avg = it.getAveragePrice()
                priceView.text = "${avg.toMoney(it.stock)} ➡ ${it.stock?.getPriceString()}"

                val profit = it.getProfitAmount()
                priceChangeAbsoluteView.text = profit.toMoney(it.stock)

                val percent = it.getProfitPercent() * sign(it.lots.toDouble()) // инвертировать доходность шорта

                val totalCash = it.balance * avg + profit
                cashView.text = totalCash.toMoney(it.stock)

                val emoji = Utils.getEmojiForPercent(percent)
                priceChangePercentView.text = percent.toPercent() + emoji

                priceView.setTextColor(Utils.getColorForValue(percent))
                priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(percent))
                priceChangePercentView.setTextColor(Utils.getColorForValue(percent))
                orderbookButton.setOnClickListener { _ ->
                    it.stock?.let { stock ->
                        orderbookManager.start(stock)
                        orderbookButton.findNavController().navigate(R.id.action_nav_portfolio_position_to_nav_orderbook)
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