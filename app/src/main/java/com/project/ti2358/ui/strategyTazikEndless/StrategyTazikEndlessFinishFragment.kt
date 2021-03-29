package com.project.ti2358.ui.strategyTazikEndless

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
import com.project.ti2358.data.manager.SettingsManager
import com.project.ti2358.data.manager.StrategyTazikEndless
import com.project.ti2358.databinding.FragmentTazikEndlessFinishBinding
import com.project.ti2358.databinding.FragmentTazikEndlessFinishItemBinding
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class StrategyTazikEndlessFinishFragment : Fragment(R.layout.fragment_tazik_endless_finish) {
    val strategyTazikEndless: StrategyTazikEndless by inject()

    private var fragmentTazikEndlessFinishBinding: FragmentTazikEndlessFinishBinding? = null

    var adapterList: ItemTazikRecyclerViewAdapter = ItemTazikRecyclerViewAdapter(emptyList())
    var positions: MutableList<PurchaseStock> = mutableListOf()
    var startTime: String = ""

    override fun onDestroy() {
        fragmentTazikEndlessFinishBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentTazikEndlessFinishBinding.bind(view)
        fragmentTazikEndlessFinishBinding = binding

        binding.list.addItemDecoration(DividerItemDecoration(binding.list.context, DividerItemDecoration.VERTICAL))
        binding.list.layoutManager = LinearLayoutManager(context)
        binding.list.adapter = adapterList

        binding.startButton.setOnClickListener {
            tryStartTazik()
        }

        positions = strategyTazikEndless.getPurchaseStock()
        adapterList.setData(positions)

        updateInfoText()
        updateServiceButtonText()
    }

    fun tryStartTazik() {
        if (SettingsManager.getTazikPurchaseVolume() <= 0 || SettingsManager.getTazikPurchaseParts() == 0) {
            Utils.showMessageAlert(requireContext(),"В настройках не задана общая сумма покупки или количество частей, раздел Автотазик.")
        } else {
            if (Utils.isServiceRunning(requireContext(), StrategyTazikService::class.java)) {
                requireContext().stopService(Intent(context, StrategyTazikService::class.java))
                strategyTazikEndless.stopStrategy()
            } else {
                if (strategyTazikEndless.stocksToPurchase.size > 0) {
                    Utils.startService(requireContext(), StrategyTazikService::class.java)
                    strategyTazikEndless.startStrategy()
                }
            }
        }
        updateServiceButtonText()
    }

    fun updateInfoText() {
        val percent = SettingsManager.getTazikChangePercent()
        val volume = SettingsManager.getTazikPurchaseVolume().toDouble()
        val p = SettingsManager.getTazikPurchaseParts()
        val parts = "%d по %.2f$".format(p, volume / p)
        startTime = SettingsManager.getTazikNearestTime()

        val prepareText: String = TheApplication.application.applicationContext.getString(R.string.prepare_start_tazik_buy_text)
        fragmentTazikEndlessFinishBinding?.infoTextView?.text = String.format(
            prepareText,
            positions.size,
            percent,
            volume,
            parts,
            startTime
        )
    }

    private fun updateServiceButtonText() {
        if (Utils.isServiceRunning(requireContext(), StrategyTazikService::class.java)) {
            fragmentTazikEndlessFinishBinding?.startButton?.text = getString(R.string.stop)
        } else {
            fragmentTazikEndlessFinishBinding?.startButton?.text = getString(R.string.start_now)
        }
    }

    inner class ItemTazikRecyclerViewAdapter(private var values: List<PurchaseStock>) : RecyclerView.Adapter<ItemTazikRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<PurchaseStock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FragmentTazikEndlessFinishItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentTazikEndlessFinishItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val purchaseStock = values[index]

                with(binding) {
                    val avg = purchaseStock.stock.getPriceDouble()
                    tickerView.text = "${index + 1}) ${purchaseStock.stock.getTickerLove()} x ${purchaseStock.lots}"
                    priceView.text = "${purchaseStock.stock.getPrice2359String()} ➡ ${avg.toMoney(purchaseStock.stock)}"
                    priceTotalView.text = (purchaseStock.stock.getPriceDouble() * purchaseStock.lots).toMoney(purchaseStock.stock)

                    refreshPercent(purchaseStock)

                    percentPlusButton.setOnClickListener {
                        purchaseStock.addPriceLimitPercent(0.05)
                        refreshPercent(purchaseStock)
                        updateInfoText()
                    }

                    percentMinusButton.setOnClickListener {
                        purchaseStock.addPriceLimitPercent(-0.05)
                        refreshPercent(purchaseStock)
                        updateInfoText()
                    }

                    itemView.setBackgroundColor(Utils.getColorForIndex(index))
                }
            }

            private fun refreshPercent(purchaseStock: PurchaseStock) {
                val percent = purchaseStock.percentLimitPriceChange

                with(binding) {
                    priceChangePercentView.text = percent.toPercent()
                    priceChangeAbsoluteView.text = purchaseStock.absoluteLimitPriceChange.toMoney(purchaseStock.stock)
                    priceChangeAbsoluteTotalView.text = (purchaseStock.absoluteLimitPriceChange * purchaseStock.lots).toMoney(purchaseStock.stock)

                    priceBuyView.text = purchaseStock.getLimitPriceDouble().toMoney(purchaseStock.stock)
                    priceBuyTotalView.text = (purchaseStock.getLimitPriceDouble() * purchaseStock.lots).toMoney(purchaseStock.stock)

                    priceChangePercentView.setTextColor(Utils.getColorForValue(percent))
                    priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(percent))
                    priceChangeAbsoluteTotalView.setTextColor(Utils.getColorForValue(percent))
                }
            }
        }
    }
}