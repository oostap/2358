package com.project.ti2358.ui.strategyDayLow

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.*
import com.project.ti2358.databinding.FragmentDaylowStartBinding
import com.project.ti2358.databinding.FragmentDaylowStartItemBinding
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.util.*

@KoinApiExtension
class StrategyDayLowStartFragment : Fragment(R.layout.fragment_daylow_start) {
    val strategyDayLow: StrategyDayLow by inject()
    private val orderbookManager: OrderbookManager by inject()
    private val chartManager: ChartManager by inject()

    private var fragmentDaylowStartBinding: FragmentDaylowStartBinding? = null

    var adapterList: Item2358RecyclerViewAdapter = Item2358RecyclerViewAdapter(emptyList())
    lateinit var stocks: MutableList<Stock>

    override fun onDestroy() {
        fragmentDaylowStartBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentDaylowStartBinding.bind(view)
        fragmentDaylowStartBinding = binding

        with(binding) {
            list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapterList

            startButton.setOnClickListener {
                if (strategyDayLow.stocksSelected.isNotEmpty()) {
                    view.findNavController().navigate(R.id.action_nav_daylow_start_to_nav_daylow_finish)
                } else {
                    Utils.showErrorAlert(requireContext())
                }
            }

            updateButton.setOnClickListener {
                stocks = strategyDayLow.process()
                adapterList.setData(stocks)
            }

            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    processText(query)
                    return false
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    processText(newText)
                    return false
                }

                fun processText(text: String) {
                    stocks = strategyDayLow.process()

                    stocks = Utils.search(stocks, text)
                    adapterList.setData(stocks)
                }
            })
            searchView.requestFocus()

            searchView.setOnCloseListener {
                stocks = strategyDayLow.process()
                adapterList.setData(stocks)
                false
            }
        }

        stocks = strategyDayLow.process()
        adapterList.setData(stocks)
    }

    inner class Item2358RecyclerViewAdapter(private var values: List<Stock>) : RecyclerView.Adapter<Item2358RecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<Stock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FragmentDaylowStartItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentDaylowStartItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val stock = values[index]

                with(binding) {
                    chooseView.setOnCheckedChangeListener(null)
                    chooseView.isChecked = strategyDayLow.isSelected(stock)

                    tickerView.text = "${index + 1}) ${stock.getTickerLove()}"
                    priceView.text = "${stock.getPrice2359String()} ➡ ${stock.getPriceString()}"
                    priceLowView.text = "${stock.lowPrice.toMoney(stock)} ➡ ${stock.getPriceString()}"

                    priceChangeLowAbsoluteView.text = stock.changePriceLowDayAbsolute.toMoney(stock)
                    priceChangeLowPercentView.text = stock.changePriceLowDayPercent.toPercent()
                    priceChangeLowAbsoluteView.setTextColor(Utils.getColorForValue(stock.changePriceLowDayPercent))
                    priceChangeLowPercentView.setTextColor(Utils.getColorForValue(stock.changePriceLowDayPercent))

                    priceChangeAbsoluteView.text = stock.changePrice2300DayAbsolute.toMoney(stock)
                    priceChangePercentView.text = stock.changePrice2300DayPercent.toPercent()
                    priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(stock.changePrice2300DayPercent))
                    priceChangePercentView.setTextColor(Utils.getColorForValue(stock.changePrice2300DayPercent))

                    sectorView.text = stock.getSectorName()
                    sectorView.setTextColor(Utils.getColorForSector(stock.closePrices?.sector))

                    chooseView.setOnCheckedChangeListener { _, checked ->
                        strategyDayLow.setSelected(stock, checked)
                    }

                    itemView.setOnClickListener {
                        Utils.openOrderbookForStock(findNavController(), orderbookManager, stock)
                    }

                    chartButton.setOnClickListener {
                        Utils.openChartForStock(findNavController(), chartManager, stock)
                    }

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