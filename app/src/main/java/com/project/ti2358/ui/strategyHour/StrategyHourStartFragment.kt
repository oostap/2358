package com.project.ti2358.ui.strategyHour

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.manager.StrategyHour
import com.project.ti2358.data.model.dto.Interval
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class StrategyHourStartFragment : Fragment() {

    val strategyHour: StrategyHour by inject()
    var adapterList: Item1005RecyclerViewAdapter = Item1005RecyclerViewAdapter(emptyList())

    var interval: Interval = Interval.HOUR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_hour_start, container, false)
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

        val buttonHour1 = view.findViewById<Button>(R.id.buttonHour1)
        buttonHour1.setOnClickListener {
            interval = Interval.HOUR
            updateData()

        }
        val buttonHour2 = view.findViewById<Button>(R.id.buttonHour2)
        buttonHour2.setOnClickListener {
            interval = Interval.TWO_HOURS
            updateData()
        }

        updateData()
        return view
    }

    private fun updateData() {
        strategyHour.process()
        adapterList.setData(strategyHour.resort(interval))
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
                R.layout.fragment_hour_start_item,
                parent,
                false
            )

            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.stock = item

            var changeAbsolute = 0.0
            var changePercent = 0.0
            var startPrice = 0.0

            if (interval == Interval.HOUR) {
                changeAbsolute = item.changePriceHour1Absolute
                changePercent = item.changePriceHour1Percent
                startPrice = item.changePriceHour1Start
            } else if (interval == Interval.TWO_HOURS) {
                changeAbsolute = item.changePriceHour2Absolute
                changePercent = item.changePriceHour2Percent
                startPrice = item.changePriceHour2Start
            }

            holder.tickerView.text = "${position + 1}) ${item.marketInstrument.ticker}"
            holder.priceView.text = "${startPrice.toDollar()} ➡ ${item.getPriceString()}"

            val volume = item.getTodayVolume() / 1000f
            holder.volumeTodayView.text = "%.1fk".format(volume)

            val volumeCash = item.dayVolumeCash / 1000f / 1000f
            holder.volumeTodayCashView.text = "%.2f B$".format(volumeCash)


            holder.changePriceAbsoluteView.text = changeAbsolute.toDollar()
            holder.changePricePercentView.text = changePercent.toPercent()

            if (changeAbsolute < 0) {
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