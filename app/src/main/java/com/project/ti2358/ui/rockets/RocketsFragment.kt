package com.project.ti2358.ui.rockets

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.OrderbookManager
import com.project.ti2358.data.manager.RocketStock
import com.project.ti2358.data.manager.StrategyRocket
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class RocketsFragment : Fragment() {
    val orderbookManager: OrderbookManager by inject()
    val strategyRocket: StrategyRocket by inject()
    var adapterList: ItemRocketRecyclerViewAdapter = ItemRocketRecyclerViewAdapter(emptyList())
    lateinit var buttonStart: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_rockets, container, false)
        val list = view.findViewById<RecyclerView>(R.id.list)

        list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))

        if (list is RecyclerView) {
            with(list) {
                layoutManager = LinearLayoutManager(context)
                adapter = adapterList
            }
        }

        buttonStart = view.findViewById(R.id.button_start)
        buttonStart.setOnClickListener { _ ->
            if (Utils.isServiceRunning(requireContext(), StrategyRocketService::class.java)) {
                requireContext().stopService(Intent(context, StrategyRocketService::class.java))
            } else {
                Utils.startService(requireContext(), StrategyRocketService::class.java)
            }
            updateServiceButtonText()
        }
        updateServiceButtonText()

        val buttonRocket = view.findViewById<Button>(R.id.button_rocket)
        buttonRocket.setOnClickListener { _ ->
            adapterList.setData(strategyRocket.rocketStocks)
        }

        val buttonComet = view.findViewById<Button>(R.id.button_comet)
        buttonComet.setOnClickListener { _ ->
            adapterList.setData(strategyRocket.cometStocks)
        }

        adapterList.setData(strategyRocket.rocketStocks)
        return view
    }

    private fun updateServiceButtonText() {
        if (Utils.isServiceRunning(requireContext(), StrategyRocketService::class.java)) {
            buttonStart.text = getString(R.string.stop)
        } else {
            buttonStart.text = getString(R.string.start)
        }
    }

    inner class ItemRocketRecyclerViewAdapter(
        private var values: List<RocketStock>
    ) : RecyclerView.Adapter<ItemRocketRecyclerViewAdapter.ViewHolder>() {

        fun setData(newValues: List<RocketStock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.fragment_rockets_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.rocketStock = item

            holder.tickerView.text = "${position + 1}) ${item.stock.instrument.ticker}"

            val volume = item.stock.getTodayVolume() / 1000f
            holder.volumeTodayView.text = "%.1fk".format(volume)

            val volumeRocket = item.volume / 1000f
            holder.volumeFromStartView.text = "%.1fk".format(volumeRocket)

            holder.priceView.text = "${item.priceFrom.toMoney(item.stock)} ➡ ${item.priceTo.toMoney(item.stock)}"

            holder.changePriceAbsoluteView.text = item.changePriceRocketAbsolute.toMoney(item.stock)
            holder.changePricePercentView.text = item.changePriceRocketPercent.toPercent()

            holder.changePriceAbsoluteView.setTextColor(Utils.getColorForValue(item.changePriceRocketPercent))
            holder.changePricePercentView.setTextColor(Utils.getColorForValue(item.changePriceRocketPercent))
            holder.priceView.setTextColor(Utils.getColorForValue(item.changePriceRocketPercent))

            holder.itemView.setOnClickListener {
                Utils.openTinkoffForTicker(requireContext(), holder.rocketStock.stock.instrument.ticker)
            }

            holder.imageOrderbook.setOnClickListener {
                orderbookManager.start(holder.rocketStock.stock)
                holder.imageOrderbook.findNavController().navigate(R.id.action_nav_rocket_to_nav_orderbook)
            }

            holder.sectorView.text = item.stock.getSectorName()
            holder.sectorView.setTextColor(Utils.getColorForSector(item.stock.closePrices?.sector))

            holder.itemView.setOnClickListener {
                Utils.openTinkoffForTicker(requireContext(), holder.rocketStock.stock.instrument.ticker)
            }

            if (item.stock.report != null) {
                holder.reportView.text = item.stock.getReportInfo()
                holder.reportView.visibility = View.VISIBLE
            } else {
                holder.reportView.visibility = View.GONE
            }
            holder.reportView.setTextColor(Utils.RED)

            holder.itemView.setBackgroundColor(Utils.getColorForIndex(position))
        }

        override fun getItemCount(): Int = values.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            lateinit var rocketStock: RocketStock

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