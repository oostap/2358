package com.project.ti2358.ui.strategyZontikEndless

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.TheApplication
import com.project.ti2358.data.manager.PurchaseStock
import com.project.ti2358.data.manager.SettingsManager
import com.project.ti2358.data.manager.StrategyZontikEndless
import com.project.ti2358.databinding.FragmentZontikEndlessFinishBinding
import com.project.ti2358.databinding.FragmentZontikEndlessFinishItemBinding
import com.project.ti2358.service.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class StrategyZontikEndlessFinishFragment : Fragment(R.layout.fragment_zontik_endless_finish) {
    val strategyZontikEndless: StrategyZontikEndless by inject()

    private var fragmentZontikEndlessFinishBinding: FragmentZontikEndlessFinishBinding? = null

    var adapterList: ItemTazikRecyclerViewAdapter = ItemTazikRecyclerViewAdapter(emptyList())
    var positions: MutableList<PurchaseStock> = mutableListOf()
    var timeStartEnd: Pair<String, String> = Pair("", "")
    var scheduledStart: Boolean = false

    override fun onDestroy() {
        fragmentZontikEndlessFinishBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentZontikEndlessFinishBinding.bind(view)
        fragmentZontikEndlessFinishBinding = binding

        with(binding) {
            list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapterList

            startNowButton.setOnClickListener {
                tryStartZontik(false)
            }

            startLaterButton.setOnClickListener {
                if (timeStartEnd.first == "") {
                    Utils.showMessageAlert(requireContext(),"Ближайшего времени на сегодня нет, добавьте время или попробуйте запустить после 00:00")
                    return@setOnClickListener
                }
                tryStartZontik(true)
            }

            statusButton.setOnClickListener {
                view.findNavController().navigate(R.id.action_nav_zontik_endless_finish_to_nav_zontik_endless_status)
            }
        }

        GlobalScope.launch(Dispatchers.Main) {
            positions = strategyZontikEndless.getPurchaseStock()
            adapterList.setData(positions)

            updateInfoText()
            updateServiceButtonText()
            updateTitle()
        }
    }

    private fun updateTitle() {
        if (isAdded) {
            val act = requireActivity() as AppCompatActivity
            act.supportActionBar?.title = "Бесконечный ☂️ (${positions.size} шт.)"
        }
    }

    private fun tryStartZontik(scheduled : Boolean) {
        if (SettingsManager.getZontikEndlessPurchaseVolume() <= 0 || SettingsManager.getZontikEndlessPurchaseParts() == 0) {
            Utils.showMessageAlert(requireContext(),"В настройках не задана общая сумма покупки или количество частей, раздел Бесконечный зонт")
        } else {
            if (Utils.isServiceRunning(requireContext(), StrategyZontikEndlessService::class.java)) {
                requireContext().stopService(Intent(context, StrategyZontikEndlessService::class.java))
            } else {
                if (strategyZontikEndless.stocksToPurchase.size > 0) {
                    Utils.startService(requireContext(), StrategyZontikEndlessService::class.java)
                    GlobalScope.launch {
                        strategyZontikEndless.prepareStrategy(scheduled, timeStartEnd)
                    }
                }
            }
        }
        scheduledStart = scheduled
        updateServiceButtonText()
    }

    fun updateInfoText() {
        val percent = "%.2f".format(if (strategyZontikEndless.started) strategyZontikEndless.basicPercentLimitPriceChange else SettingsManager.getZontikEndlessChangePercent())
        val volume = SettingsManager.getZontikEndlessPurchaseVolume().toDouble()
        val p = SettingsManager.getZontikEndlessPurchaseParts()
        val parts = "%d по %.2f$".format(p, volume / p)
        timeStartEnd = SettingsManager.getZontikEndlessNearestTime()

        val start = if (timeStartEnd.first != "") timeStartEnd.first else "???"
        val end = if (timeStartEnd.second != "") timeStartEnd.second else "???"

        val prepareText: String = TheApplication.application.applicationContext.getString(R.string.prepare_start_tazik_buy_text)
        fragmentZontikEndlessFinishBinding?.infoTextView?.text = String.format(
            prepareText,
            percent,
            volume,
            parts,
            start,
            end
        )
    }

    private fun updateServiceButtonText() {
        if (Utils.isServiceRunning(requireContext(), StrategyZontikEndlessService::class.java)) {
            if (scheduledStart) {
                fragmentZontikEndlessFinishBinding?.startLaterButton?.text = getString(R.string.stop)
            } else {
                fragmentZontikEndlessFinishBinding?.startNowButton?.text = getString(R.string.stop)
            }
        } else {
            if (scheduledStart) {
                fragmentZontikEndlessFinishBinding?.startLaterButton?.text = getString(R.string.start_schedule)
            } else {
                fragmentZontikEndlessFinishBinding?.startNowButton?.text = getString(R.string.start_now)
            }
        }
    }

    inner class ItemTazikRecyclerViewAdapter(private var values: List<PurchaseStock>) : RecyclerView.Adapter<ItemTazikRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<PurchaseStock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FragmentZontikEndlessFinishItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentZontikEndlessFinishItemBinding) : RecyclerView.ViewHolder(binding.root) {
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