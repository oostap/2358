package com.project.ti2358.ui.strategyFixPrice

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.*
import com.project.ti2358.databinding.FragmentFixpriceBinding
import com.project.ti2358.databinding.FragmentFixpriceItemBinding
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.util.*

@KoinApiExtension
class StrategyFixPriceFragment : Fragment(R.layout.fragment_fixprice) {
    val orderbookManager: OrderbookManager by inject()
    val chartManager: ChartManager by inject()
    val strategyFixPrice: StrategyFixPrice by inject()

    private var fragmentFixpriceBinding: FragmentFixpriceBinding? = null

    var adapterList: ItemFixPriceRecyclerViewAdapter = ItemFixPriceRecyclerViewAdapter(emptyList())

    override fun onDestroy() {
        fragmentFixpriceBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentFixpriceBinding.bind(view)
        fragmentFixpriceBinding = binding

        with(binding) {
            list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapterList

            resetButton.setOnClickListener { // сброс времени отслеживания
                strategyFixPrice.restartStrategy()
                updateTime()
                updateData()
            }

            updateButton.setOnClickListener {
                updateData()
            }
        }

        updateData()
        updateTime()
    }

    private fun updateData() {
        strategyFixPrice.process()
        adapterList.setData(strategyFixPrice.resort())
    }

    private fun updateTime() {
        val time = strategyFixPrice.strategyStartTime.time.toString("HH:mm:ss") + " - NOW"
        val act = requireActivity() as AppCompatActivity
        act.supportActionBar?.title = time
    }

    inner class ItemFixPriceRecyclerViewAdapter(private var values: List<Stock>) : RecyclerView.Adapter<ItemFixPriceRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<Stock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FragmentFixpriceItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentFixpriceItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val stock = values[index]

                with(binding) {
                    tickerView.text = "${index + 1}) ${stock.getTickerLove()}"

                    val volume = stock.getTodayVolume() / 1000f
                    volumeSharesView.text = "%.2fk".format(locale = Locale.US, volume)

                    val volumeBefore = stock.getVolumeFixPriceBeforeStart() / 1000f
                    val volumeAfter = stock.getVolumeFixPriceAfterStart() / 1000f
                    volumeSharesFromStartView.text = "%.2fk+%.2fk".format(locale = Locale.US, volumeBefore, volumeAfter)

                    priceView.text = "${stock.priceFixed.toMoney(stock)} ➡ ${stock.getPriceString()}"

                    priceChangeAbsoluteView.text = stock.changePriceFixDayAbsolute.toMoney(stock)
                    priceChangePercentView.text = stock.changePriceFixDayPercent.toPercent()

                    priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(stock.changePriceFixDayPercent))
                    priceChangePercentView.setTextColor(Utils.getColorForValue(stock.changePriceFixDayPercent))
                    priceView.setTextColor(Utils.getColorForValue(stock.changePriceFixDayPercent))

                    itemView.setOnClickListener {
                        Utils.openOrderbookForStock(findNavController(), orderbookManager, stock)
                    }

                    chartButton.setOnClickListener {
                        Utils.openChartForStock(findNavController(), chartManager, stock)
                    }

                    sectorView.text = stock.getSectorName()
                    sectorView.setTextColor(Utils.getColorForSector(stock.closePrices?.sector))

                    if (stock.report != null) {
                        reportInfoView.text = stock.getReportInfo()
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