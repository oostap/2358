package com.project.ti2358.ui.strategyDayLow

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.TheApplication
import com.project.ti2358.data.manager.StockPurchase
import com.project.ti2358.data.manager.SettingsManager
import com.project.ti2358.data.manager.StrategyDayLow
import com.project.ti2358.databinding.FragmentDaylowFinishBinding
import com.project.ti2358.databinding.FragmentDaylowFinishItemBinding
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.util.*

@KoinApiExtension
class StrategyDayLowFinishFragment : Fragment(R.layout.fragment_daylow_finish) {
    val strategyDayLow: StrategyDayLow by inject()

    private var fragmentDaylowFinishBinding: FragmentDaylowFinishBinding? = null

    var adapterList: Item2358RecyclerViewAdapter = Item2358RecyclerViewAdapter(emptyList())
    var stockPurchases: MutableList<StockPurchase> = mutableListOf()

    override fun onDestroy() {
        fragmentDaylowFinishBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentDaylowFinishBinding.bind(view)
        fragmentDaylowFinishBinding = binding

        with(binding) {
            list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapterList

            updateServiceButtonText()

            startButton.setOnClickListener {
                if (SettingsManager.get2358PurchaseVolume() <= 0) {
                    Utils.showMessageAlert(requireContext(), "В настройках не задана общая сумма покупки, раздел 2358.")
                } else {
                    if (Utils.isServiceRunning(requireContext(), StrategyDayLowService::class.java)) {
                        requireContext().stopService(Intent(context, StrategyDayLowService::class.java))
                    } else {
                        if (strategyDayLow.getTotalPurchasePieces() > 0) {
                            Utils.startService(requireContext(), StrategyDayLowService::class.java)
                        }
                    }
                }
                updateServiceButtonText()
            }
            chooseView.setOnCheckedChangeListener { _, checked ->
                updateEqualParts(checked)
            }
        }

        stockPurchases = strategyDayLow.getPurchaseStock(true)
        adapterList.setData(stockPurchases)

        updateInfoText()

        updateEqualParts(strategyDayLow.equalParts)
    }

    private fun updateEqualParts(newValue: Boolean) {
        strategyDayLow.equalParts = newValue
        fragmentDaylowFinishBinding?.chooseView?.isChecked = newValue

        if (newValue) {
            stockPurchases = strategyDayLow.getPurchaseStock(true)
            adapterList.setData(stockPurchases)
            updateInfoText()
        }
    }

    private fun updateInfoText() {
        val time = SettingsManager.get2358PurchaseTime()
        val prepareText: String = TheApplication.application.applicationContext.getString(R.string.prepare_start_2358_text)
        fragmentDaylowFinishBinding?.infoTextView?.text = String.format(
            prepareText,
            time,
            stockPurchases.size,
            strategyDayLow.getTotalPurchaseString()
        )
    }

    private fun updateServiceButtonText() {
        if (Utils.isServiceRunning(requireContext(), Strategy2358Service::class.java)) {
            fragmentDaylowFinishBinding?.startButton?.text = getString(R.string.stop)
        } else {
            fragmentDaylowFinishBinding?.startButton?.text = getString(R.string.start)
        }
    }

    inner class Item2358RecyclerViewAdapter(private var values: List<StockPurchase>) : RecyclerView.Adapter<Item2358RecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<StockPurchase>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FragmentDaylowFinishItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentDaylowFinishItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val purchaseStock = values[index]

                with(binding) {
                    tickerView.text = "${index + 1}) ${purchaseStock.stock.getTickerLove()}"
                    priceView.text = purchaseStock.stock.getPriceString()

                    refreshPercent(purchaseStock, 0.0)
                    pricePlusButton.setOnClickListener {
                        refreshPercent(purchaseStock, 0.05)
                    }

                    priceMinusButton.setOnClickListener {
                        refreshPercent(purchaseStock, -0.05)
                    }

                    lotsPlusButton.setOnClickListener {
                        updateEqualParts(false)
                        purchaseStock.addLots(1)
                        refreshPercent(purchaseStock, 0.0)
                    }

                    lotsMinusButton.setOnClickListener {
                        updateEqualParts(false)
                        purchaseStock.addLots(-1)
                        refreshPercent(purchaseStock, 0.0)
                    }

                    chooseView.setOnCheckedChangeListener { _, checked ->
                        purchaseStock.trailingStop = checked
                        refreshPercent(purchaseStock, 0.0)
                    }

                    itemView.setBackgroundColor(Utils.getColorForIndex(index))
                }
            }

            private fun refreshPercent(stockPurchase: StockPurchase, delta: Double) {
                with(binding) {
                    chooseView.isChecked = stockPurchase.trailingStop
                    lotsView.text = "${stockPurchase.lots} шт."

                    if (stockPurchase.trailingStop) {
                        stockPurchase.addPriceProfit2358TrailingTakeProfit(delta)
                        profitPercentFromView.text = stockPurchase.trailingStopTakeProfitPercentActivation.toPercent()
                        profitPercentToView.text = stockPurchase.trailingStopTakeProfitPercentDelta.toPercent()

                        profitPercentFromView.setTextColor(Utils.PURPLE)
                        profitPercentToView.setTextColor(Utils.PURPLE)
                    } else {
                        stockPurchase.addPriceProfit2358Percent(delta)
                        profitPercentFromView.text = stockPurchase.percentProfitSellFrom.toPercent()
                        profitPercentToView.text = stockPurchase.percentProfitSellTo.toPercent()

                        profitPercentFromView.setTextColor(Utils.GREEN)
                        profitPercentToView.setTextColor(Utils.GREEN)
                    }

                    priceBuyView.text = "%.2f$".format(locale = Locale.US, stockPurchase.stock.getPriceNow() * stockPurchase.lots)
                    updateInfoText()
                }
            }
        }
    }
}