package com.project.ti2358.ui.strategy2358

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.SearchView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.manager.Strategy2358
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class Strategy2358StartFragment : Fragment() {

    val strategy2358: Strategy2358 by inject()
    var adapterList: Item2358RecyclerViewAdapter = Item2358RecyclerViewAdapter(emptyList())
    lateinit var stocks: MutableList<Stock>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_2358_start, container, false)
        val list = view.findViewById<RecyclerView>(R.id.list)

        list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))

        if (list is RecyclerView) {
            with(list) {
                layoutManager = LinearLayoutManager(context)
                adapter = adapterList
            }
        }

        val buttonStart = view.findViewById<Button>(R.id.buttonStart)
        buttonStart.setOnClickListener {
            if (strategy2358.stocksSelected.isNotEmpty()) {
                view.findNavController().navigate(R.id.action_nav_2358_start_to_nav_2358_finish)
            } else {
                Utils.showErrorAlert(requireContext())
            }
        }

        val buttonUpdate = view.findViewById<Button>(R.id.button_update)
        buttonUpdate.setOnClickListener {
            stocks = strategy2358.process()
            adapterList.setData(stocks)
        }

        stocks = strategy2358.process()
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
                stocks = strategy2358.process()

                stocks = Utils.search(stocks, text)
                adapterList.setData(stocks)
            }
        })
        searchView.requestFocus()

        searchView.setOnCloseListener {
            stocks = strategy2358.process()
            adapterList.setData(stocks)
            false
        }

        return view
    }

    inner class Item2358RecyclerViewAdapter(
        private var values: List<Stock>
    ) : RecyclerView.Adapter<Item2358RecyclerViewAdapter.ViewHolder>() {

        fun setData(newValues: List<Stock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(
                R.layout.fragment_2358_start_item,
                parent,
                false
            )

            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.stock = item

            holder.checkBoxView.setOnCheckedChangeListener(null)
            holder.checkBoxView.isChecked = strategy2358.isSelected(item)

            holder.tickerView.text = "${position + 1}) ${item.instrument.ticker}"
            holder.priceView.text = "${item.getPrice2359String()} ➡ ${item.getPriceString()}"

            val volume = item.getTodayVolume() / 1000f
            holder.volumeTodayView.text = "%.1fk".format(volume)

            val volumeCash = item.dayVolumeCash / 1000f / 1000f
            holder.volumeTodayCashView.text = "%.2fM$".format(volumeCash)

            holder.changePriceAbsoluteView.text = item.changePrice2359DayAbsolute.toMoney(item)
            holder.changePricePercentView.text = item.changePrice2359DayPercent.toPercent()

            holder.sectorView.text = item.getSectorName()
            holder.sectorView.setTextColor(Utils.getColorForSector(item.closePrices?.sector))

            holder.changePriceAbsoluteView.setTextColor(Utils.getColorForValue(item.changePrice2359DayAbsolute))
            holder.changePricePercentView.setTextColor(Utils.getColorForValue(item.changePrice2359DayAbsolute))

            holder.checkBoxView.setOnCheckedChangeListener { _, checked ->
                strategy2358.setSelected(holder.stock, checked)
            }

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
            holder.itemView.setBackgroundColor(Utils.getColorForIndex(position))
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

            val checkBoxView: CheckBox = view.findViewById(R.id.check_box)

            val reportView: TextView = view.findViewById(R.id.stock_report_info)
            val sectorView: TextView = view.findViewById(R.id.stock_sector)
        }
    }
}