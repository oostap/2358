package com.project.ti2358.ui.strategyFixPrice

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.*
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class StrategyFixPriceFragment : Fragment() {
    val orderbookManager: OrderbookManager by inject()
    val strategyFixPrice: StrategyFixPrice by inject()
    var adapterList: ItemFixPriceRecyclerViewAdapter = ItemFixPriceRecyclerViewAdapter(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_fixprice, container, false)
        val list = view.findViewById<RecyclerView>(R.id.list)

        list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))

        if (list is RecyclerView) {
            with(list) {
                layoutManager = LinearLayoutManager(context)
                adapter = adapterList
            }
        }

        val buttonReset = view.findViewById<Button>(R.id.button_restart)
        buttonReset.setOnClickListener { // сброс времени отслеживания
            strategyFixPrice.restartStrategy()
            updateTime()
            updateData()
        }

        val buttonUpdate = view.findViewById<Button>(R.id.button_update)
        buttonUpdate.setOnClickListener {
            updateData()
        }

        updateData()

        updateTime()
        return view
    }

    private fun updateData() {
        strategyFixPrice.process()
        adapterList.setData(strategyFixPrice.resort())
    }

    private fun updateTime() {
        val time = StrategyFixPrice.strategyStartTime.time.toString("HH:mm:ss") + " - ..."
        val act = requireActivity() as AppCompatActivity
        act.supportActionBar?.title = time
    }

    inner class ItemFixPriceRecyclerViewAdapter(
        private var values: List<Stock>
    ) : RecyclerView.Adapter<ItemFixPriceRecyclerViewAdapter.ViewHolder>() {

        fun setData(newValues: List<Stock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.fragment_fixprice_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.stock = item

            holder.tickerView.text = "${position + 1}) ${item.getTickerLove()}"

            val volume = item.getTodayVolume() / 1000f
            holder.volumeTodayView.text = "%.1fk".format(volume)

            val volumeBefore = item.getVolumeFixPriceBeforeStart() / 1000f
            val volumeAfter = item.getVolumeFixPriceAfterStart() / 1000f
            holder.volumeFromStartView.text = "%.1fk+%.1fk".format(volumeBefore, volumeAfter)

            holder.priceView.text = "${item.getPriceFixPriceString()} ➡ ${item.getPriceString()}"

            holder.changePriceAbsoluteView.text = item.changePriceFixDayAbsolute.toMoney(item)
            holder.changePricePercentView.text = item.changePriceFixDayPercent.toPercent()

            holder.changePriceAbsoluteView.setTextColor(Utils.getColorForValue(item.changePriceFixDayPercent))
            holder.changePricePercentView.setTextColor(Utils.getColorForValue(item.changePriceFixDayPercent))
            holder.priceView.setTextColor(Utils.getColorForValue(item.changePriceFixDayPercent))

            holder.itemView.setOnClickListener {
                Utils.openTinkoffForTicker(requireContext(), holder.stock.ticker)
            }

            holder.imageOrderbook.setOnClickListener {
                orderbookManager.start(holder.stock)
                holder.imageOrderbook.findNavController().navigate(R.id.action_nav_fixprice_to_nav_orderbook)
            }

            holder.sectorView.text = item.getSectorName()
            holder.sectorView.setTextColor(Utils.getColorForSector(item.closePrices?.sector))

            holder.itemView.setOnClickListener {
                Utils.openTinkoffForTicker(requireContext(), holder.stock.ticker)
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
            val volumeFromStartView: TextView = view.findViewById(R.id.stock_item_volume_from_start)

            val changePriceAbsoluteView: TextView = view.findViewById(R.id.stock_item_price_change_absolute)
            val changePricePercentView: TextView = view.findViewById(R.id.stock_item_price_change_percent)

            val imageOrderbook: ImageView = view.findViewById(R.id.orderbook)

            val reportView: TextView = view.findViewById(R.id.stock_report_info)
            val sectorView: TextView = view.findViewById(R.id.stock_sector)
        }
    }
}