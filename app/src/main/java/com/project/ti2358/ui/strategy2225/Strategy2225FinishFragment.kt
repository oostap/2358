package com.project.ti2358.ui.strategy2225

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
import com.project.ti2358.data.manager.Strategy2225
import com.project.ti2358.databinding.Fragment2225FinishBinding
import com.project.ti2358.databinding.Fragment2225FinishItemBinding
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.util.*

@KoinApiExtension
class Strategy2225FinishFragment : Fragment(R.layout.fragment_2225_finish) {
    val strategy2225: Strategy2225 by inject()

    private var fragment2225FinishBinding: Fragment2225FinishBinding? = null

    var adapterList: Item2358RecyclerViewAdapter = Item2358RecyclerViewAdapter(emptyList())
    var stockPurchases: MutableList<StockPurchase> = mutableListOf()

    override fun onDestroy() {
        fragment2225FinishBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = Fragment2225FinishBinding.bind(view)
        fragment2225FinishBinding = binding

        with(binding) {
            list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapterList

            updateServiceButtonText()

            startButton.setOnClickListener {
                if (SettingsManager.get2225PurchaseVolume() <= 0) {
                    Utils.showMessageAlert(requireContext(), "В настройках не задана общая сумма шорта, раздел 2225.")
                } else {
                    if (Utils.isServiceRunning(requireContext(), Strategy2225Service::class.java)) {
                        requireContext().stopService(Intent(context, Strategy2225Service::class.java))
                    } else {
                        if (strategy2225.getTotalPurchasePieces() > 0) {
                            Utils.startService(requireContext(), Strategy2225Service::class.java)
                        }
                    }
                }
                updateServiceButtonText()
            }
            chooseView.setOnCheckedChangeListener { _, checked ->
                updateEqualParts(checked)
            }
        }

        stockPurchases = strategy2225.getPurchaseStock(true)
        adapterList.setData(stockPurchases)

        updateInfoText()

        updateEqualParts(strategy2225.equalParts)
    }

    private fun updateEqualParts(newValue: Boolean) {
        strategy2225.equalParts = newValue
        fragment2225FinishBinding?.chooseView?.isChecked = newValue

        if (newValue) {
            stockPurchases = strategy2225.getPurchaseStock(true)
            adapterList.setData(stockPurchases)
            updateInfoText()
        }
    }

    private fun updateInfoText() {
        val time = SettingsManager.get2225PurchaseTime()
        val prepareText: String = TheApplication.application.applicationContext.getString(R.string.prepare_start_2225_text)
        fragment2225FinishBinding?.infoTextView?.text = String.format(
            prepareText,
            time,
            stockPurchases.size,
            strategy2225.getTotalPurchaseString()
        )
    }

    private fun updateServiceButtonText() {
        if (Utils.isServiceRunning(requireContext(), Strategy2225Service::class.java)) {
            fragment2225FinishBinding?.startButton?.text = getString(R.string.stop)
        } else {
            fragment2225FinishBinding?.startButton?.text = getString(R.string.start)
        }
    }

    inner class Item2358RecyclerViewAdapter(private var values: List<StockPurchase>) : RecyclerView.Adapter<Item2358RecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<StockPurchase>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(Fragment2225FinishItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: Fragment2225FinishItemBinding) : RecyclerView.ViewHolder(binding.root) {
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

                    chooseView.visibility = View.GONE
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