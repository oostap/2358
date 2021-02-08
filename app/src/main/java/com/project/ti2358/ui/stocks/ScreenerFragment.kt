package com.project.ti2358.ui.stocks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.manager.StrategyScreener
import com.project.ti2358.service.Sorting
import com.project.ti2358.service.Utils
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class ScreenerFragment : Fragment() {

    val strategyScreener: StrategyScreener by inject()
    var adapterList: ItemStocksRecyclerViewAdapter = ItemStocksRecyclerViewAdapter(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_screener_item_list, container, false)
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
            strategyScreener.process()
            adapterList.setData(strategyScreener.resort(sort))
            sort = if (sort == Sorting.DESCENDING) {
                Sorting.ASCENDING
            } else {
                Sorting.DESCENDING
            }
        }

        strategyScreener.process()
        adapterList.setData(strategyScreener.resort(sort))

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
                R.layout.fragment_screener_item,
                parent,
                false
            )

            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.stock = item

            holder.tickerView.text = "${position}. ${item.marketInstrument.ticker}"
            holder.priceView.text = item.getPriceString()

            val volume = item.getTodayVolume() / 1000f
            holder.volumeTodayView.text = "%.1f".format(volume) + "k"

            val volumeCash = item.dayVolumeCash / 1000f / 1000f
            holder.volumeTodayCashView.text = "%.2f".format(volumeCash) + "B$"

            holder.changePriceAbsoluteView.text = "%.2f".format(item.changePriceDayAbsolute) + " $"
            holder.changePricePercentView.text = "%.2f".format(item.changePriceDayPercent) + "%"

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