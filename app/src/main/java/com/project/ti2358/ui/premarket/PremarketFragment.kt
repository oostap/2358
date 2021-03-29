package com.project.ti2358.ui.premarket

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
import com.project.ti2358.data.manager.*
import com.project.ti2358.databinding.FragmentPremarketBinding
import com.project.ti2358.databinding.FragmentPremarketItemBinding
import com.project.ti2358.service.Utils
import com.project.ti2358.service.toMoney
import com.project.ti2358.service.toPercent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class PremarketFragment : Fragment(R.layout.fragment_premarket) {
    val stockManager: StockManager by inject()
    val strategyPremarket: StrategyPremarket by inject()
    val strategy1005: Strategy1005 by inject()
    val orderbookManager: OrderbookManager by inject()

    private var fragmentPremarketBinding: FragmentPremarketBinding? = null

    var adapterList: ItemPremarketRecyclerViewAdapter = ItemPremarketRecyclerViewAdapter(emptyList())
    lateinit var stocks: MutableList<Stock>
    var postmarket: Boolean = true
    var job: Job? = null

    override fun onDestroy() {
        job?.cancel()
        fragmentPremarketBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentPremarketBinding.bind(view)
        fragmentPremarketBinding = binding

        binding.list.addItemDecoration(DividerItemDecoration(binding.list.context, DividerItemDecoration.VERTICAL))
        binding.list.layoutManager = LinearLayoutManager(context)
        binding.list.adapter = adapterList

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                processText(query)
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                processText(newText)
                return false
            }

            fun processText(text: String) {
                updateData(false)

                stocks = Utils.search(stocks, text)
                adapterList.setData(stocks)
            }
        })

        binding.searchView.setOnCloseListener {
            updateData()
            false
        }

        binding.premarketButton.setOnClickListener {
            postmarket = false
            updateData(false)

            stocks = Utils.search(stocks, binding.searchView.query.toString())
            adapterList.setData(stocks)
        }

        binding.postmarketButton.setOnClickListener {
            postmarket = true
            updateData(false)

            stocks = Utils.search(stocks, binding.searchView.query.toString())
            adapterList.setData(stocks)
        }

        job?.cancel()
        job = GlobalScope.launch(Dispatchers.Main) {
            stockManager.reloadClosePrices()
            updateData()
        }

        updateData()
    }

    fun updateData(update: Boolean = true) {
        if (postmarket) {
            stocks = strategy1005.process()
            stocks = strategy1005.resort()
        } else {
            stocks = strategyPremarket.process()
            stocks = strategyPremarket.resort()
        }

        if (update) adapterList.setData(stocks)
    }

    inner class ItemPremarketRecyclerViewAdapter(private var values: List<Stock>) : RecyclerView.Adapter<ItemPremarketRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<Stock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FragmentPremarketItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentPremarketItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val stock = values[index]
                with(binding) {
                    tickerView.text = "${index + 1}) ${stock.getTickerLove()}"

                    val volume = stock.getTodayVolume() / 1000f
                    volumeSharesView.text = "%.1fk".format(volume)

                    val volumeCash = stock.dayVolumeCash / 1000f / 1000f
                    volumeCashView.text = "%.2fM$".format(volumeCash)

                    if (postmarket) {
                        priceView.text = "${stock.getPrice2359String()} ➡ ${stock.getPriceString()}"

                        priceChangeAbsoluteView.text = stock.changePrice2359DayAbsolute.toMoney(stock)
                        priceChangePercentView.text = stock.changePrice2359DayPercent.toPercent()

                        priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(stock.changePrice2359DayAbsolute))
                        priceChangePercentView.setTextColor(Utils.getColorForValue(stock.changePrice2359DayAbsolute))
                        priceView.setTextColor(Utils.getColorForValue(stock.changePrice2359DayAbsolute))
                    } else {
                        priceView.text = "${stock.getPricePost1000String()} ➡ ${stock.getPriceString()}"

                        priceChangeAbsoluteView.text = stock.changePriceDayAbsolute.toMoney(stock)
                        priceChangePercentView.text = stock.changePriceDayPercent.toPercent()

                        priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(stock.changePriceDayAbsolute))
                        priceChangePercentView.setTextColor(Utils.getColorForValue(stock.changePriceDayAbsolute))
                        priceView.setTextColor(Utils.getColorForValue(stock.changePriceDayAbsolute))
                    }

                    itemView.setOnClickListener {
                        Utils.openTinkoffForTicker(requireContext(), stock.ticker)
                    }

                    orderbookButton.setOnClickListener {
                        orderbookManager.start(stock)
                        orderbookButton.findNavController().navigate(R.id.action_nav_premarket_to_nav_orderbook)
                    }

                    sectorView.text = stock.getSectorName()
                    sectorView.setTextColor(Utils.getColorForSector(stock.closePrices?.sector))

                    itemView.setOnClickListener {
                        Utils.openTinkoffForTicker(requireContext(), stock.ticker)
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