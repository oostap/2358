package com.project.ti2358.ui.strategyShorts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.manager.StrategyShorts
import com.project.ti2358.databinding.FragmentShortsBinding
import com.project.ti2358.databinding.FragmentShortsItemBinding
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class StrategyShortsFragment : Fragment(R.layout.fragment_shorts) {
    private val strategyShorts: StrategyShorts by inject()

    private var fragmentShortsBinding: FragmentShortsBinding? = null

    var adapterList: Item1005RecyclerViewAdapter = Item1005RecyclerViewAdapter(emptyList())

    override fun onDestroy() {
        fragmentShortsBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentShortsBinding.bind(view)
        fragmentShortsBinding = binding

        binding.list.addItemDecoration(DividerItemDecoration(binding.list.context, DividerItemDecoration.VERTICAL))
        binding.list.layoutManager = LinearLayoutManager(context)
        binding.list.adapter = adapterList

        binding.updateButton.setOnClickListener {
            updateData()
        }

        updateData()
    }

    private fun updateData() {
        strategyShorts.process()
        adapterList.setData(strategyShorts.resort())
    }

    inner class Item1005RecyclerViewAdapter(private var values: List<Stock>) : RecyclerView.Adapter<Item1005RecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<Stock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FragmentShortsItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentShortsItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val stock = values[index]

                with(binding) {
                    tickerView.text = "${index + 1}) ${stock.getTickerLove()}"
                    priceView.text = "${stock.getPrice2359String()} ➡ ${stock.getPriceString()}"

                    val volume = stock.getTodayVolume() / 1000f
                    volumeSharesView.text = "%.1fk".format(volume)

                    val volumeCash = stock.dayVolumeCash / 1000f / 1000f
                    volumeCashView.text = "%.2fM$".format(volumeCash)

                    priceChangeAbsoluteView.text = stock.changePrice2359DayAbsolute.toMoney(stock)
                    priceChangePercentView.text = stock.changePrice2359DayPercent.toPercent()

                    priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(stock.changePrice2359DayAbsolute))
                    priceChangePercentView.setTextColor(Utils.getColorForValue(stock.changePrice2359DayAbsolute))

                    itemView.setOnClickListener {
                        Utils.openTinkoffForTicker(requireContext(), stock.ticker)
                    }

                    itemView.setBackgroundColor(Utils.getColorForIndex(index))
                }
            }
        }
    }
}