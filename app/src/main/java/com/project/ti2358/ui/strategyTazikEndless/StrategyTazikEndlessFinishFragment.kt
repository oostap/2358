package com.project.ti2358.ui.strategyTazikEndless

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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

        with(binding) {
            list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapterList

            startButton.setOnClickListener {
                tryStartTazik()
            }

            statusButton.setOnClickListener {
                view.findNavController().navigate(R.id.action_nav_tazik_endless_finish_to_nav_tazik_endless_status)
            }
        }

        positions = strategyTazikEndless.getPurchaseStock()
        adapterList.setData(positions)

        updateInfoText()
        updateServiceButtonText()
    }

    fun tryStartTazik() {
        if (SettingsManager.getTazikEndlessPurchaseVolume() <= 0 || SettingsManager.getTazikEndlessPurchaseParts() == 0) {
            Utils.showMessageAlert(requireContext(),"В настройках не задана общая сумма покупки или количество частей, раздел Бесконечный таз")
        } else {
            if (Utils.isServiceRunning(requireContext(), StrategyTazikEndlessService::class.java)) {
                requireContext().stopService(Intent(context, StrategyTazikEndlessService::class.java))
            } else {
                if (strategyTazikEndless.stocksToPurchase.size > 0) {
                    Utils.startService(requireContext(), StrategyTazikEndlessService::class.java)
                    GlobalScope.launch {
                        strategyTazikEndless.startStrategy()
                    }
                }
            }
        }
        updateServiceButtonText()
    }

    fun updateInfoText() {
        val percent = if (strategyTazikEndless.started) strategyTazikEndless.basicPercentLimitPriceChange else SettingsManager.getTazikEndlessChangePercent()
        val volume = SettingsManager.getTazikEndlessPurchaseVolume().toDouble()
        val p = SettingsManager.getTazikEndlessPurchaseParts()
        val parts = "%d по %.2f$".format(p, volume / p)
        startTime = "сейчас"

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
        if (Utils.isServiceRunning(requireContext(), StrategyTazikEndlessService::class.java)) {
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
                    val avg = purchaseStock.stock.getPriceNow()
                    tickerView.text = "${index + 1}) ${purchaseStock.stock.getTickerLove()} x ${purchaseStock.lots}"
                    priceView.text = "${purchaseStock.stock.getPrice2359String()} ➡ ${avg.toMoney(purchaseStock.stock)}"
                    priceTotalView.text = (purchaseStock.stock.getPriceNow() * purchaseStock.lots).toMoney(purchaseStock.stock)

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