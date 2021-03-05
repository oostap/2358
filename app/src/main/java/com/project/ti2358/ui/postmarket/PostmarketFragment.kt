package com.project.ti2358.ui.postmarket

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SearchView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.manager.StrategyPostmarket
import com.project.ti2358.service.Sorting
import com.project.ti2358.service.Utils
import com.project.ti2358.service.toDollar
import com.project.ti2358.service.toPercent
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class PostmarketFragment : Fragment() {

    val strategyPostmarket: StrategyPostmarket by inject()
    var adapterList: ItemStocksRecyclerViewAdapter = ItemStocksRecyclerViewAdapter(emptyList())
    lateinit var stocks: MutableList<Stock>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_postmarket_item_list, container, false)
        val list = view.findViewById<RecyclerView>(R.id.list)

        if (list is RecyclerView) {
            with(list) {
                layoutManager = LinearLayoutManager(context)
                adapter = adapterList
            }
        }

        var sort = Sorting.DESCENDING
        val buttonSort = view.findViewById<Button>(R.id.buttonSort)
        buttonSort.setOnClickListener {
            stocks = strategyPostmarket.process()
            stocks = strategyPostmarket.resort(sort)
            adapterList.setData(stocks)
            sort = if (sort == Sorting.DESCENDING) {
                Sorting.ASCENDING
            } else {
                Sorting.DESCENDING
            }
        }

        stocks = strategyPostmarket.process()
        stocks = strategyPostmarket.resort(sort)
        adapterList.setData(stocks)

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
                strategyPostmarket.process()
                stocks = strategyPostmarket.resort(sort)

                if (text.isNotEmpty()) {
                    stocks = stocks.filter {
                        it.marketInstrument.ticker.contains(text, ignoreCase = true) || it.marketInstrument.name.contains(text, ignoreCase = true)
                    } as MutableList<Stock>
                }
                adapterList.setData(stocks)
            }
        })

        searchView.setOnCloseListener {
            stocks = strategyPostmarket.process()
            adapterList.setData(stocks)
            false
        }

        return view
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

            var ticker = "${position + 1} ${item.marketInstrument.ticker}"
            item.yahooPostmarket?.exchange?.let {
                ticker += " (${it})"
            }
            holder.tickerView.text = ticker

            val priceFrom = item.getPriceDouble().toDollar()
            val priceTo = item.getPricePostmarketUSDouble().toDollar()
            holder.priceView.text = "$priceFrom ➡ $priceTo"

            val volume = item.getPostmarketVolume() / 1000f
            holder.volumeTodayView.text = "%.1fk".format(volume)

            val volumeCash = item.getPostmarketVolumeCash() / 1000f / 1000f
            holder.volumeTodayCashView.text = "%.2fB$".format(volumeCash)

            holder.changePriceAbsoluteView.text = item.changePricePostmarketAbsolute.toDollar()
            holder.changePricePercentView.text = item.changePricePostmarketPercent.toPercent()

            if (item.changePricePostmarketAbsolute < 0) {
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