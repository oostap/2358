package com.project.ti2358.ui.premarket

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SearchView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.manager.StrategyPremarket
import com.project.ti2358.service.Sorting
import com.project.ti2358.service.Utils
import com.project.ti2358.service.toDollar
import com.project.ti2358.service.toPercent
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class PremarketFragment : Fragment() {

    val strategyPremarket: StrategyPremarket by inject()
    var adapterList: ItemStocksRecyclerViewAdapter = ItemStocksRecyclerViewAdapter(emptyList())
    lateinit var stocks: MutableList<Stock>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_premarket_item_list, container, false)
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

        val buttonSort = view.findViewById<Button>(R.id.buttonSort)
        buttonSort.setOnClickListener {
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
            stocks = strategyPremarket.process()
            adapterList.setData(stocks)
            false
        }

        updateData()
        return view
    }

    fun updateData() {
        stocks = strategyPremarket.process()
        stocks = strategyPremarket.resort()
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

            holder.tickerView.text = "${position + 1}) ${item.marketInstrument.ticker}"
            holder.priceView.text = "${item.getPrice1000String()} ➡ ${item.getPriceString()}"

            val volume = item.getTodayVolume() / 1000f
            holder.volumeTodayView.text = "%.1fk".format(volume)

            val volumeCash = item.dayVolumeCash / 1000f / 1000f
            holder.volumeTodayCashView.text = "%.2fB$".format(volumeCash)

            holder.changePriceAbsoluteView.text = item.changePriceDayAbsolute.toDollar()
            holder.changePricePercentView.text = item.changePriceDayPercent.toPercent()

            if (item.changePriceDayAbsolute < 0) {
                holder.changePriceAbsoluteView.setTextColor(Utils.RED)
                holder.changePricePercentView.setTextColor(Utils.RED)
            } else {
                holder.changePriceAbsoluteView.setTextColor(Utils.GREEN)
                holder.changePricePercentView.setTextColor(Utils.GREEN)
            }

            holder.itemView.setOnClickListener {
                Utils.openTinkoffForTicker(requireContext(), holder.stock.marketInstrument.ticker)
            }
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
        }
    }
}