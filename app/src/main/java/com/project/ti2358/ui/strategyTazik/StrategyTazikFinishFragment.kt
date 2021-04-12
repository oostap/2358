package com.project.ti2358.ui.strategyTazik

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
import com.project.ti2358.data.manager.StrategyTazik
import com.project.ti2358.data.manager.SettingsManager
import com.project.ti2358.data.manager.StockManager
import com.project.ti2358.databinding.FragmentTazikFinishBinding
import com.project.ti2358.databinding.FragmentTazikFinishItemBinding
import com.project.ti2358.service.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class StrategyTazikFinishFragment : Fragment(R.layout.fragment_tazik_finish) {
    val strategyTazik: StrategyTazik by inject()
    val stockManager: StockManager by inject()

    private var fragmentTazikFinishBinding: FragmentTazikFinishBinding? = null

    var adapterList: ItemTazikRecyclerViewAdapter = ItemTazikRecyclerViewAdapter(emptyList())
    var positions: MutableList<PurchaseStock> = mutableListOf()
    var startTime: String = ""
    var scheduledStart: Boolean = false
    var job: Job? = null

    override fun onDestroy() {
        fragmentTazikFinishBinding = null
        job?.cancel()
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentTazikFinishBinding.bind(view)
        fragmentTazikFinishBinding = binding

        with(binding) {
            list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapterList

            startNowButton.setOnClickListener {
                tryStartTazik(false)
            }

            startLaterButton.setOnClickListener {
                tryStartTazik(true)
            }
        }

        positions = strategyTazik.getPurchaseStock()
        adapterList.setData(positions)

        updateInfoText()
        updateServiceButtonText()

        job?.cancel()
        job = GlobalScope.launch(Dispatchers.IO) {
            stockManager.reloadClosePrices()
        }
    }

    fun tryStartTazik(scheduled : Boolean) {
        if (SettingsManager.getTazikPurchaseVolume() <= 0 || SettingsManager.getTazikPurchaseParts() == 0) {
            Utils.showMessageAlert(requireContext(),"В настройках не задана общая сумма покупки или количество частей, раздел Автотазик.")
        } else {
            if (Utils.isServiceRunning(requireContext(), StrategyTazikService::class.java)) {
                requireContext().stopService(Intent(context, StrategyTazikService::class.java))
                strategyTazik.stopStrategy()
            } else {
                if (strategyTazik.stocksToPurchase.size > 0) {
                    Utils.startService(requireContext(), StrategyTazikService::class.java)
                    strategyTazik.prepareStrategy(scheduled, startTime)
                }
            }
        }
        scheduledStart = scheduled
        updateServiceButtonText()
    }

    fun updateInfoText() {
        val percent = SettingsManager.getTazikChangePercent()
        val volume = SettingsManager.getTazikPurchaseVolume().toDouble()
        val p = SettingsManager.getTazikPurchaseParts()
        val parts = "%d по %.2f$".format(p, volume / p)
        startTime = SettingsManager.getTazikNearestTime()

        val prepareText: String = TheApplication.application.applicationContext.getString(R.string.prepare_start_tazik_buy_text)
        fragmentTazikFinishBinding?.infoTextView?.text = String.format(
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
            if (scheduledStart) {
                fragmentTazikFinishBinding?.startLaterButton?.text = getString(R.string.stop)
            } else {
                fragmentTazikFinishBinding?.startNowButton?.text = getString(R.string.stop)
            }
        } else {
            if (scheduledStart) {
                fragmentTazikFinishBinding?.startLaterButton?.text = getString(R.string.stop)
            } else {
                fragmentTazikFinishBinding?.startNowButton?.text = getString(R.string.start_now)
            }
        }
    }

    inner class ItemTazikRecyclerViewAdapter(private var values: List<PurchaseStock>) : RecyclerView.Adapter<ItemTazikRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<PurchaseStock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FragmentTazikFinishItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentTazikFinishItemBinding) : RecyclerView.ViewHolder(binding.root) {
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