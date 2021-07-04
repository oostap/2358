package com.project.ti2358.ui.trends

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
import com.project.ti2358.data.manager.OrderbookManager
import com.project.ti2358.data.manager.StrategyTrend
import com.project.ti2358.data.manager.StockTrend
import com.project.ti2358.databinding.FragmentTrendsBinding
import com.project.ti2358.databinding.FragmentTrendsItemBinding
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.util.*

@KoinApiExtension
class TAFragment : Fragment(R.layout.fragment_ta) {
    val orderbookManager: OrderbookManager by inject()
    val strategyTrend: StrategyTrend by inject()

    private var fragmentTrendsBinding: FragmentTrendsBinding? = null

    var adapterList: ItemRocketRecyclerViewAdapter = ItemRocketRecyclerViewAdapter(emptyList())

    override fun onDestroy() {
        fragmentTrendsBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentTrendsBinding.bind(view)
        fragmentTrendsBinding = binding

        with(binding) {
            list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapterList

            startButton.setOnClickListener {
                if (Utils.isServiceRunning(requireContext(), StrategyTrendService::class.java)) {
                    requireContext().stopService(Intent(context, StrategyTrendService::class.java))
                } else {
                    Utils.startService(requireContext(), StrategyTrendService::class.java)
                }
                updateServiceButtonText()
            }
            updateServiceButtonText()

            upButton.setOnClickListener {
                adapterList.setData(strategyTrend.upStockTrends)
            }

            downButton.setOnClickListener {
                adapterList.setData(strategyTrend.downStockTrends)
            }
        }

        adapterList.setData(strategyTrend.upStockTrends)
    }

    private fun updateServiceButtonText() {
        if (Utils.isServiceRunning(requireContext(), StrategyTrendService::class.java)) {
            fragmentTrendsBinding?.startButton?.text = getString(R.string.stop)
        } else {
            fragmentTrendsBinding?.startButton?.text = getString(R.string.start)
        }
    }

    inner class ItemRocketRecyclerViewAdapter(private var values: List<StockTrend>) : RecyclerView.Adapter<ItemRocketRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<StockTrend>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FragmentTrendsItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentTrendsItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val trendStock = values[index]
                with(binding) {
                    tickerView.text = "${index + 1}) ${trendStock.stock.getTickerLove()}"

                    val deltaMinutes = ((Calendar.getInstance().time.time - trendStock.fireTime) / 60.0 / 1000.0).toInt()
                    minutesView.text = "$deltaMinutes мин"

                    val volume = trendStock.stock.getTodayVolume() / 1000f
                    volumeSharesView.text = "%.1fk".format(volume)

                    volumeSharesFromStartView.text = "${trendStock.timeFromStartToLow} / ${trendStock.timeFromLowToNow} мин"

                    priceStartLowView.text = "${trendStock.priceStart.toMoney(trendStock.stock)} ➡ ${trendStock.priceLow.toMoney(trendStock.stock)}"
                    priceLowNowView.text = "${trendStock.priceLow.toMoney(trendStock.stock)} ➡ ${trendStock.priceNow.toMoney(trendStock.stock)}"

                    priceChangeAbsoluteView.text = trendStock.changeFromStartToLow.toMoney(trendStock.stock)
                    priceChangePercentView.text = trendStock.changeFromLowToNow.toPercent()

                    priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(trendStock.changeFromStartToLow))
                    priceChangePercentView.setTextColor(Utils.getColorForValue(trendStock.changeFromStartToLow))
                    priceStartLowView.setTextColor(Utils.getColorForValue(trendStock.changeFromStartToLow))
                    priceLowNowView.setTextColor(Utils.getColorForValue(trendStock.changeFromStartToLow))

                    itemView.setOnClickListener {
                        Utils.openOrderbookForStock(it.findNavController(), orderbookManager, trendStock.stock)
                    }

                    orderbookButton.setOnClickListener {
                        Utils.openOrderbookForStock(it.findNavController(), orderbookManager, trendStock.stock)
                    }

                    sectorView.text = trendStock.stock.getSectorName()
                    sectorView.setTextColor(Utils.getColorForSector(trendStock.stock.closePrices?.sector))

                    if (trendStock.stock.report != null) {
                        reportInfoView.text = trendStock.stock.getReportInfo()
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