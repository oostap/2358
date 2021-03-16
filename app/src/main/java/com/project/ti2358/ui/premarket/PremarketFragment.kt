package com.project.ti2358.ui.premarket

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SearchView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.*
import com.project.ti2358.service.Utils
import com.project.ti2358.service.toMoney
import com.project.ti2358.service.toPercent
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class PremarketFragment : Fragment() {
    val strategyPremarket: StrategyPremarket by inject()
    val strategy1005: Strategy1005 by inject()

    val orderbookManager: OrderbookManager by inject()
    var adapterList: ItemStocksRecyclerViewAdapter = ItemStocksRecyclerViewAdapter(emptyList())
    lateinit var stocks: MutableList<Stock>

    var closemarket: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_premarket, container, false)
        val list = view.findViewById<RecyclerView>(R.id.list)

        list.addItemDecoration(
            DividerItemDecoration(
                list.context,
                DividerItemDecoration.VERTICAL
            )
        )

        if (list is RecyclerView) {
            with(list) {
                layoutManager = LinearLayoutManager(context)
                adapter = adapterList
            }
        }

        val buttonPremarket = view.findViewById<Button>(R.id.button_premarket)
        buttonPremarket.setOnClickListener {
            closemarket = false
            updateData()
        }

        val buttonClosemarket = view.findViewById<Button>(R.id.button_closemarket)
        buttonClosemarket.setOnClickListener {
            closemarket = true
            updateData()
        }

        val searchView: SearchView = view.findViewById(R.id.searchView)
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
                updateData()

                stocks = Utils.search(stocks, text)
                adapterList.setData(stocks)
            }
        })

        searchView.setOnCloseListener {
            updateData()
            false
        }

        updateData()
        return view
    }

    fun updateData() {
        if (closemarket) {
            stocks = strategy1005.process()
            stocks = strategy1005.resort()
        } else {
            stocks = strategyPremarket.process()
            stocks = strategyPremarket.resort()
        }
        adapterList.setData(stocks)
    }

    inner class ItemStocksRecyclerViewAdapter(
        private var values: List<Stock>
    ) : RecyclerView.Adapter<ItemStocksRecyclerViewAdapter.ViewHolder>() {

        fun setData(newValues: List<Stock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(
                R.layout.fragment_premarket_item,
                parent,
                false
            )

            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.stock = item

            holder.tickerView.text = "${position + 1}) ${item.instrument.ticker}"
            holder.priceView.text = "${item.getPrice1000String()} ➡ ${item.getPriceString()}"

            val volume = item.getTodayVolume() / 1000f
            holder.volumeTodayView.text = "%.1fk".format(volume)

            val volumeCash = item.dayVolumeCash / 1000f / 1000f
            holder.volumeTodayCashView.text = "%.2fB$".format(volumeCash)

            if (closemarket) {
                holder.changePriceAbsoluteView.text = item.changePrice2359DayAbsolute.toMoney(item)
                holder.changePricePercentView.text = item.changePrice2359DayPercent.toPercent()

                holder.changePriceAbsoluteView.setTextColor(Utils.getColorForValue(item.changePrice2359DayAbsolute))
                holder.changePricePercentView.setTextColor(Utils.getColorForValue(item.changePrice2359DayAbsolute))
            } else {
                holder.changePriceAbsoluteView.text = item.changePriceDayAbsolute.toMoney(item)
                holder.changePricePercentView.text = item.changePriceDayPercent.toPercent()

                holder.changePriceAbsoluteView.setTextColor(Utils.getColorForValue(item.changePriceDayAbsolute))
                holder.changePricePercentView.setTextColor(Utils.getColorForValue(item.changePriceDayAbsolute))
            }

            holder.itemView.setOnClickListener {
                Utils.openTinkoffForTicker(requireContext(), holder.stock.instrument.ticker)
            }

            holder.buttonOrderbook.setOnClickListener {
                orderbookManager.start(holder.stock)
                holder.buttonOrderbook.findNavController().navigate(R.id.action_nav_premarket_to_nav_orderbook)
            }

            holder.sectorView.text = item.getSectorName()
            holder.sectorView.setTextColor(item.closePrices?.sector?.getColor() ?: Color.BLACK)

            holder.itemView.setOnClickListener {
                Utils.openTinkoffForTicker(requireContext(), holder.stock.instrument.ticker)
            }

            if (item.report != null) {
                holder.reportView.text = item.getReportInfo()
                holder.reportView.visibility = View.VISIBLE
            } else {
                holder.reportView.visibility = View.GONE
            }
            holder.reportView.setTextColor(Utils.RED)
        }

        override fun getItemCount(): Int = values.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            lateinit var stock: Stock

            val tickerView: TextView = view.findViewById(R.id.stock_item_ticker)
            val priceView: TextView = view.findViewById(R.id.stock_item_price)

            val volumeTodayView: TextView = view.findViewById(R.id.stock_item_volume_today)
            val volumeTodayCashView: TextView = view.findViewById(R.id.stock_item_volume_today_cash)

            val changePriceAbsoluteView: TextView = view.findViewById(R.id.stock_item_price_change_absolute)
            val changePricePercentView: TextView = view.findViewById(R.id.stock_item_price_change_percent)

            val buttonOrderbook: Button = view.findViewById(R.id.button_orderbook)

            val reportView: TextView = view.findViewById(R.id.stock_report_info)
            val sectorView: TextView = view.findViewById(R.id.stock_sector)
        }
    }
}