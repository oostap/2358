package com.project.ti2358.ui.strategy2225

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.OrderbookManager
import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.manager.Strategy2225
import com.project.ti2358.databinding.Fragment2225StartBinding
import com.project.ti2358.databinding.Fragment2225StartItemBinding
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.util.*

@KoinApiExtension
class Strategy2225StartFragment : Fragment(R.layout.fragment_2225_start) {
    val strategy2225: Strategy2225 by inject()
    private val orderbookManager: OrderbookManager by inject()

    private var fragment2225StartBinding: Fragment2225StartBinding? = null

    var adapterList: Item2358RecyclerViewAdapter = Item2358RecyclerViewAdapter(emptyList())
    lateinit var stocks: MutableList<Stock>

    override fun onDestroy() {
        fragment2225StartBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = Fragment2225StartBinding.bind(view)
        fragment2225StartBinding = binding

        with(binding) {
            list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapterList

            startButton.setOnClickListener {
                if (strategy2225.stocksSelected.isNotEmpty()) {
                    view.findNavController().navigate(R.id.action_nav_2225_start_to_nav_2225_finish)
                } else {
                    Utils.showErrorAlert(requireContext())
                }
            }

            updateButton.setOnClickListener {
                stocks = strategy2225.process()
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
                    stocks = strategy2225.process()

                    stocks = Utils.search(stocks, text)
                    adapterList.setData(stocks)
                }
            })
            searchView.requestFocus()

            searchView.setOnCloseListener {
                stocks = strategy2225.process()
                adapterList.setData(stocks)
                false
            }
        }

        stocks = strategy2225.process()
        adapterList.setData(stocks)
    }

    inner class Item2358RecyclerViewAdapter(private var values: List<Stock>) : RecyclerView.Adapter<Item2358RecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<Stock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(Fragment2225StartItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: Fragment2225StartItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val stock = values[index]

                with(binding) {
                    chooseView.setOnCheckedChangeListener(null)
                    chooseView.isChecked = strategy2225.isSelected(stock)

                    tickerView.text = "${index + 1}) ${stock.getTickerLove()}"
                    priceView.text = "${stock.getPrice2359String()} ➡ ${stock.getPriceString()}"

                    val volume = stock.getTodayVolume() / 1000f
                    volumeSharesView.text = "%.1fk".format(locale = Locale.US, volume)

                    val volumeCash = stock.dayVolumeCash / 1000f / 1000f
                    volumeCashView.text = "%.2fM$".format(locale = Locale.US, volumeCash)

                    priceChangeAbsoluteView.text = stock.changePrice2300DayAbsolute.toMoney(stock)
                    priceChangePercentView.text = stock.changePrice2300DayPercent.toPercent()

                    sectorView.text = stock.getSectorName()
                    sectorView.setTextColor(Utils.getColorForSector(stock.closePrices?.sector))

                    priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(stock.changePrice2300DayAbsolute))
                    priceChangePercentView.setTextColor(Utils.getColorForValue(stock.changePrice2300DayAbsolute))

                    chooseView.setOnCheckedChangeListener { _, checked ->
                        strategy2225.setSelected(stock, checked)
                    }

                    itemView.setOnClickListener {
                        view?.findNavController()?.let {
                            Utils.openOrderbookForStock(it, orderbookManager, stock)
                        }
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