package com.project.ti2358.ui.strategy1005

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
import com.project.ti2358.data.service.Stock
import com.project.ti2358.data.service.Strategy1005
import com.project.ti2358.data.service.StrategyScreener
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@KoinApiExtension
class Strategy1005StartFragment : Fragment() {

    val strategy1005: Strategy1005 by inject()
    var adapterList: Item1005RecyclerViewAdapter = Item1005RecyclerViewAdapter(emptyList())

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
//            if (strategy2358.stocksSelected.isNotEmpty()) {
//                view.findNavController().navigate(R.id.action_nav_2358_start_to_nav_2358_finish)
//            } else {
//                Utils.showErrorAlert(requireContext())
//            }
        }

        val checkBox = view.findViewById<CheckBox>(R.id.check_box)
        checkBox.setOnCheckedChangeListener { _, isChecked ->
//            for (stock in strategy2358.process()) {
//                strategy2358.setSelected(stock, !isChecked)
//            }
//            adapterList.notifyDataSetChanged()
        }

        var sort = Sorting.DESCENDING
        val buttonUpdate = view.findViewById<Button>(R.id.buttonUpdate)
        buttonUpdate.setOnClickListener {
            strategy1005.process()
            adapterList.setData(strategy1005.resort(sort))
            sort = if (sort == Sorting.DESCENDING) {
                Sorting.ASCENDING
            } else {
                Sorting.DESCENDING
            }
        }

        strategy1005.process()
        adapterList.setData(strategy1005.resort(sort))

        return view
    }

    inner class Item1005RecyclerViewAdapter(
        private var values: List<Stock>
    ) : RecyclerView.Adapter<Item1005RecyclerViewAdapter.ViewHolder>() {

        fun setData(newValues: List<Stock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(
                R.layout.fragment_1005_start_item,
                parent,
                false
            )

            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.stock = item

            holder.checkBoxView.setOnCheckedChangeListener(null)
            holder.checkBoxView.isChecked = false//strategy2358.isSelected(item)

            holder.tickerView.text = "${position}. ${item.marketInstrument.ticker}"
            holder.priceView.text = "${item.getClosingPriceString()} -> ${item.getPriceString()}"

            val volume = item.getTodayVolume() / 1000f
            holder.volumeTodayView.text = "%.1fk".format(volume)

            val volumeCash = item.dayVolumeCash / 1000f / 1000f
            holder.volumeTodayCashView.text = "%.2f B$".format(volumeCash)

            holder.changePriceAbsoluteView.text = "%.2f $".format(item.changePriceFromClosingDayAbsolute)
            holder.changePricePercentView.text = "%.2f".format(item.changePriceFromClosingDayPercent) + "%"

            if (item.changePriceFromClosingDayAbsolute < 0) {
                holder.changePriceAbsoluteView.setTextColor(Utils.RED)
                holder.changePricePercentView.setTextColor(Utils.RED)
            } else {
                holder.changePriceAbsoluteView.setTextColor(Utils.GREEN)
                holder.changePricePercentView.setTextColor(Utils.GREEN)
            }

            holder.checkBoxView.setOnCheckedChangeListener { _, isChecked ->
//                strategy2358.setSelected(holder.stock, !isChecked)
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

            override fun toString(): String {
                return super.toString() + " '" + tickerView.text + "'"
            }
        }
    }
}