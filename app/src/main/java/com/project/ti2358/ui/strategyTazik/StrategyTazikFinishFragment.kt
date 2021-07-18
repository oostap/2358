package com.project.ti2358.ui.strategyTazik

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
import com.project.ti2358.data.manager.StockPurchase
import com.project.ti2358.data.manager.StrategyTazik
import com.project.ti2358.data.manager.SettingsManager
import com.project.ti2358.data.manager.StockManager
import com.project.ti2358.databinding.FragmentTazikFinishBinding
import com.project.ti2358.databinding.FragmentTazikFinishItemBinding
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class StrategyTazikFinishFragment : Fragment(R.layout.fragment_tazik_finish) {
    val strategyTazik: StrategyTazik by inject()
    val stockManager: StockManager by inject()

    private var fragmentTazikFinishBinding: FragmentTazikFinishBinding? = null

    var adapterList: ItemTazikRecyclerViewAdapter = ItemTazikRecyclerViewAdapter(emptyList())
    var positions: MutableList<StockPurchase> = mutableListOf()
    var timeStartEnd: Pair<String, String> = Pair("", "")
    var scheduledStart: Boolean = false

    override fun onDestroy() {
        fragmentTazikFinishBinding = null
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
                if (timeStartEnd.first == "") {
                    Utils.showMessageAlert(requireContext(),"Ближайшего времени на сегодня нет, добавьте время или попробуйте запустить после 00:00")
                    return@setOnClickListener
                }
                tryStartTazik(true)
            }

            statusButton.setOnClickListener {
                view.findNavController().navigate(R.id.action_nav_tazik_finish_to_nav_tazik_status)
            }
        }

        positions = strategyTazik.getPurchaseStock()
        adapterList.setData(positions)

        updateInfoText()
        updateServiceButtonText()
    }

    fun tryStartTazik(scheduled : Boolean) {
        if (SettingsManager.getTazikPurchaseVolume() <= 0 || SettingsManager.getTazikPurchaseParts() == 0) {
            Utils.showMessageAlert(requireContext(),"В настройках не задана общая сумма покупки или количество частей, раздел Автотазик.")
        } else {
            if (Utils.isServiceRunning(requireContext(), StrategyTazikService::class.java)) {
                requireContext().stopService(Intent(context, StrategyTazikService::class.java))
            } else {
                if (strategyTazik.stocksToPurchase.size > 0) {
                    Utils.startService(requireContext(), StrategyTazikService::class.java)
                    strategyTazik.prepareStrategy(scheduled, timeStartEnd)
                }
            }
        }
        scheduledStart = scheduled
        updateServiceButtonText()
    }

    fun updateInfoText() {
        val percent = "%.2f".format(if (strategyTazik.started) strategyTazik.basicPercentLimitPriceChange else SettingsManager.getTazikChangePercent())
        val volume = SettingsManager.getTazikPurchaseVolume().toDouble()
        val p = SettingsManager.getTazikPurchaseParts()
        val parts = "%d по %.2f$".format(p, volume / p)

        timeStartEnd = SettingsManager.getTazikNearestTime()

        val start = if (timeStartEnd.first != "") timeStartEnd.first else "???"
        val end = if (timeStartEnd.second != "") timeStartEnd.second else "???"

        val prepareText: String = TheApplication.application.applicationContext.getString(R.string.prepare_start_tazik_buy_text)
        fragmentTazikFinishBinding?.infoTextView?.text = String.format(
            prepareText,
            percent,
            volume,
            parts,
            start,
            end
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

    inner class ItemTazikRecyclerViewAdapter(private var values: List<StockPurchase>) : RecyclerView.Adapter<ItemTazikRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<StockPurchase>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FragmentTazikFinishItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentTazikFinishItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val purchase = values[index]

                with(binding) {
                    val avg = purchase.stock.getPriceNow()
                    tickerView.text = "${index + 1}) ${purchase.stock.getTickerLove()} x ${purchase.lots}"
                    priceView.text = "${purchase.stock.getPrice2359String()} ➡ ${avg.toMoney(purchase.stock)}"
                    priceTotalView.text = (purchase.stock.getPriceNow() * purchase.lots).toMoney(purchase.stock)

                    refreshPercent(purchase)

                    percentPlusButton.setOnClickListener {
                        purchase.addPriceLimitPercent(0.05)
                        refreshPercent(purchase)
                        updateInfoText()
                    }

                    percentMinusButton.setOnClickListener {
                        purchase.addPriceLimitPercent(-0.05)
                        refreshPercent(purchase)
                        updateInfoText()
                    }

                    if (SettingsManager.getBrokerAlor() && SettingsManager.getBrokerTinkoff()) {
                        itemView.setBackgroundColor(Utils.getColorForBrokerValue(purchase.broker))
                    } else {
                        itemView.setBackgroundColor(Utils.getColorForIndex(index))
                    }
                }
            }

            private fun refreshPercent(stockPurchase: StockPurchase) {
                val percent = stockPurchase.percentLimitPriceChange

                with(binding) {
                    priceChangePercentView.text = percent.toPercent()
                    priceChangeAbsoluteView.text = stockPurchase.absoluteLimitPriceChange.toMoney(stockPurchase.stock)
                    priceChangeAbsoluteTotalView.text = (stockPurchase.absoluteLimitPriceChange * stockPurchase.lots).toMoney(stockPurchase.stock)

                    priceBuyView.text = stockPurchase.getLimitPriceDouble().toMoney(stockPurchase.stock)
                    priceBuyTotalView.text = (stockPurchase.getLimitPriceDouble() * stockPurchase.lots).toMoney(stockPurchase.stock)

                    priceChangePercentView.setTextColor(Utils.getColorForValue(percent))
                    priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(percent))
                    priceChangeAbsoluteTotalView.setTextColor(Utils.getColorForValue(percent))
                }
            }
        }
    }
}