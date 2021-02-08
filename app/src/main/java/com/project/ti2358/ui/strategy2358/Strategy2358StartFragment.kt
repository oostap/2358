package com.project.ti2358.ui.strategy2358

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_2358_start, container, false)
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

        val buttonStart = view.findViewById<Button>(R.id.buttonStart)
        buttonStart.setOnClickListener {
            if (strategy2358.stocksSelected.isNotEmpty()) {
                view.findNavController().navigate(R.id.action_nav_2358_start_to_nav_2358_finish)
            } else {
                Utils.showErrorAlert(requireContext())
            }
        }

        val buttonUpdate = view.findViewById<Button>(R.id.buttonUpdate)
        buttonUpdate.setOnClickListener {
            adapterList.setData(strategy2358.process())
        }

        val checkBox = view.findViewById<CheckBox>(R.id.check_box)
        checkBox.setOnCheckedChangeListener { _, isChecked ->
            for (stock in strategy2358.process()) {
                strategy2358.setSelected(stock, !isChecked)
            }
            adapterList.notifyDataSetChanged()
        }

        adapterList.setData(strategy2358.process())

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

            holder.tickerView.text = "${position}. ${item.marketInstrument.ticker}"
            holder.priceView.text = "${item.getPrice1000String()} -> ${item.getPriceString()}"

            val volume = item.getTodayVolume() / 1000f
            holder.volumeTodayView.text = "%.1fk".format(volume)

            val volumeCash = item.dayVolumeCash / 1000f / 1000f
            holder.volumeTodayCashView.text = "%.2f B$".format(volumeCash)

            holder.changePriceAbsoluteView.text = "%.2f $".format(item.changePrice2359DayAbsolute)
            holder.changePricePercentView.text = "%.2f".format(item.changePrice2359DayPercent) + "%"

            if (item.changePriceDayAbsolute < 0) {
                holder.changePriceAbsoluteView.setTextColor(Utils.RED)
                holder.changePricePercentView.setTextColor(Utils.RED)
            } else {
                holder.changePriceAbsoluteView.setTextColor(Utils.GREEN)
                holder.changePricePercentView.setTextColor(Utils.GREEN)
            }

            holder.checkBoxView.setOnCheckedChangeListener { _, isChecked ->
                strategy2358.setSelected(holder.stock, !isChecked)
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

            val checkBoxView: CheckBox = view.findViewById(R.id.check_box)
        }
    }
}