package com.project.ti2358.ui.arbitration

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.ChartManager
import com.project.ti2358.data.manager.OrderbookManager
import com.project.ti2358.data.manager.RocketStock
import com.project.ti2358.databinding.FragmentArbitrationBinding
import com.project.ti2358.databinding.FragmentArbitrationItemBinding
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.util.*

@KoinApiExtension
class ArbitrationFragment : Fragment(R.layout.fragment_arbitration) {
    val orderbookManager: OrderbookManager by inject()
    val chartManager: ChartManager by inject()
    val strategyArbitration: StrategyArbitration by inject()

    private var fragmentArbitrationBinding: FragmentArbitrationBinding? = null

    var adapterList: ItemRocketRecyclerViewAdapter = ItemRocketRecyclerViewAdapter(emptyList())

    override fun onDestroy() {
        fragmentArbitrationBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentArbitrationBinding.bind(view)
        fragmentArbitrationBinding = binding

        with(binding) {
            list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapterList

            startButton.setOnClickListener {
                if (Utils.isServiceRunning(requireContext(), StrategyRocketService::class.java)) {
                    requireContext().stopService(Intent(context, StrategyRocketService::class.java))
                } else {
                    Utils.startService(requireContext(), StrategyRocketService::class.java)
                }
                updateServiceButtonText()
            }
            updateServiceButtonText()

            rocketButton.setOnClickListener {
                adapterList.setData(strategyArbitration.longStocks)
            }

            cometButton.setOnClickListener {
                adapterList.setData(strategyArbitration.shortStocks)
            }
        }

        adapterList.setData(strategyArbitration.longStocks)
    }

    private fun updateServiceButtonText() {
        if (Utils.isServiceRunning(requireContext(), StrategyRocketService::class.java)) {
            fragmentArbitrationBinding?.startButton?.text = getString(R.string.stop)
        } else {
            fragmentArbitrationBinding?.startButton?.text = getString(R.string.start)
        }
    }

    inner class ItemRocketRecyclerViewAdapter(private var values: List<RocketStock>) : RecyclerView.Adapter<ItemRocketRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<RocketStock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FragmentArbitrationItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentArbitrationItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val rocketStock = values[index]
                with(binding) {
                    tickerView.text = "${index + 1}) ${rocketStock.stock.getTickerLove()}"

                    val deltaMinutes = ((Calendar.getInstance().time.time - rocketStock.fireTime) / 60.0 / 1000.0).toInt()
                    minutesView.text = "$deltaMinutes мин"

                    val volume = rocketStock.stock.getTodayVolume() / 1000f
                    volumeSharesView.text = "%.1fk".format(volume)

                    val volumeRocket = rocketStock.volume
                    volumeSharesFromStartView.text = "%d".format(volumeRocket)

                    priceView.text = "${rocketStock.priceFrom.toMoney(rocketStock.stock)} ➡ ${rocketStock.priceTo.toMoney(rocketStock.stock)}"

                    priceChangeAbsoluteView.text = rocketStock.changePriceRocketAbsolute.toMoney(rocketStock.stock)
                    priceChangePercentView.text = rocketStock.changePriceRocketPercent.toPercent()

                    priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(rocketStock.changePriceRocketPercent))
                    priceChangePercentView.setTextColor(Utils.getColorForValue(rocketStock.changePriceRocketPercent))
                    priceView.setTextColor(Utils.getColorForValue(rocketStock.changePriceRocketPercent))

                    itemView.setOnClickListener {
                        Utils.openOrderbookForStock(findNavController(), orderbookManager, rocketStock.stock)
                    }

                    chartButton.setOnClickListener {
                        Utils.openChartForStock(findNavController(), chartManager, rocketStock.stock)
                    }

                    sectorView.text = rocketStock.stock.getSectorName()
                    sectorView.setTextColor(Utils.getColorForSector(rocketStock.stock.closePrices?.sector))

                    if (rocketStock.stock.report != null) {
                        reportInfoView.text = rocketStock.stock.getReportInfo()
                        reportInfoView.visibility = View.VISIBLE
                    } else {
                        reportInfoView.visibility = View.GONE
                    }
                    reportInfoView.setTextColor(Utils.RED)
                    itemView.setBackgroundColor(Utils.getColorForIndex(index))
                }
            }
        }
    }
}