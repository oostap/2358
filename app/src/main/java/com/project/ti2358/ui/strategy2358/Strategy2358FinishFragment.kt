package com.project.ti2358.ui.strategy2358

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
import com.project.ti2358.data.manager.PurchaseStock
import com.project.ti2358.data.manager.Strategy2358
import com.project.ti2358.data.manager.SettingsManager
import com.project.ti2358.databinding.Fragment2358FinishBinding
import com.project.ti2358.databinding.Fragment2358FinishItemBinding
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class Strategy2358FinishFragment : Fragment(R.layout.fragment_2358_finish) {
    val strategy2358: Strategy2358 by inject()

    private var fragment2358FinishBinding: Fragment2358FinishBinding? = null

    var adapterList: Item2358RecyclerViewAdapter = Item2358RecyclerViewAdapter(emptyList())
    var stocks: MutableList<PurchaseStock> = mutableListOf()

    override fun onDestroy() {
        fragment2358FinishBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = Fragment2358FinishBinding.bind(view)
        fragment2358FinishBinding = binding

        binding.list.addItemDecoration(DividerItemDecoration(binding.list.context, DividerItemDecoration.VERTICAL))
        binding.list.layoutManager = LinearLayoutManager(context)
        binding.list.adapter = adapterList

        updateServiceButtonText()

        binding.startButton.setOnClickListener {
            if (SettingsManager.get2358PurchaseVolume() <= 0) {
                Utils.showMessageAlert(requireContext(), "В настройках не задана общая сумма покупки, раздел 2358.")
            } else {
                if (Utils.isServiceRunning(requireContext(), Strategy2358Service::class.java)) {
                    requireContext().stopService(Intent(context, Strategy2358Service::class.java))
                } else {
                    if (strategy2358.getTotalPurchasePieces() > 0) {
                        Utils.startService(requireContext(), Strategy2358Service::class.java)
                    }
                }
            }
            updateServiceButtonText()
        }

        stocks = strategy2358.getPurchaseStock(true)
        adapterList.setData(stocks)

        updateInfoText()

        binding.chooseView.setOnCheckedChangeListener { _, checked ->
            updateEqualParts(checked)
        }
        updateEqualParts(strategy2358.equalParts)
    }

    private fun updateEqualParts(newValue: Boolean) {
        strategy2358.equalParts = newValue
        fragment2358FinishBinding?.chooseView?.isChecked = newValue

        if (newValue) {
            stocks = strategy2358.getPurchaseStock(true)
            adapterList.setData(stocks)
            updateInfoText()
        }
    }

    private fun updateInfoText() {
        val time = SettingsManager.get2358PurchaseTime()
        val prepareText: String = TheApplication.application.applicationContext.getString(R.string.prepare_start_2358_text)
        fragment2358FinishBinding?.infoTextView?.text = String.format(
            prepareText,
            time,
            stocks.size,
            strategy2358.getTotalPurchaseString()
        )
    }

    private fun updateServiceButtonText() {
        if (Utils.isServiceRunning(requireContext(), Strategy2358Service::class.java)) {
            fragment2358FinishBinding?.startButton?.text = getString(R.string.stop)
        } else {
            fragment2358FinishBinding?.startButton?.text = getString(R.string.start)
        }
    }

    inner class Item2358RecyclerViewAdapter(private var values: List<PurchaseStock>) : RecyclerView.Adapter<Item2358RecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<PurchaseStock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(Fragment2358FinishItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: Fragment2358FinishItemBinding) : RecyclerView.ViewHolder(binding.root) {
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

            private fun refreshPercent(purchaseStock: PurchaseStock, delta: Double) {
                with(binding) {
                    chooseView.isChecked = purchaseStock.trailingStop
                    lotsView.text = "${purchaseStock.lots} шт."

                    if (purchaseStock.trailingStop) {
                        purchaseStock.addPriceProfit2358TrailingTakeProfit(delta)
                        profitPercentFromView.text = purchaseStock.trailingStopTakeProfitPercentActivation.toPercent()
                        profitPercentToView.text = purchaseStock.trailingStopTakeProfitPercentDelta.toPercent()

                        profitPercentFromView.setTextColor(Utils.PURPLE)
                        profitPercentToView.setTextColor(Utils.PURPLE)
                    } else {
                        purchaseStock.addPriceProfit2358Percent(delta)
                        profitPercentFromView.text = purchaseStock.percentProfitSellFrom.toPercent()
                        profitPercentToView.text = purchaseStock.percentProfitSellTo.toPercent()

                        profitPercentFromView.setTextColor(Utils.GREEN)
                        profitPercentToView.setTextColor(Utils.GREEN)
                    }

                    priceBuyView.text = "%.2f$".format(purchaseStock.stock.getPriceDouble() * purchaseStock.lots)
                    updateInfoText()
                }
            }
        }
    }
}