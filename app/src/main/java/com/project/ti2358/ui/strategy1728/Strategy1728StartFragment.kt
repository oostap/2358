package com.project.ti2358.ui.strategy1728

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.PurchaseStock
import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.manager.Strategy1728
import com.project.ti2358.data.service.SettingsManager
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import kotlin.math.roundToInt

@KoinApiExtension
class Strategy1728StartFragment : Fragment() {

    val strategy1728: Strategy1728 by inject()
    var adapterList: Item1728RecyclerViewAdapter = Item1728RecyclerViewAdapter(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_1728_start, container, false)
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

        var sort = Sorting.DESCENDING
        val buttonReset = view.findViewById<Button>(R.id.buttonReset)
        buttonReset.setOnClickListener { _ ->
            // сброс времени отслеживания
            strategy1728.resetStrategy()
            updateTime()

            updateData()
        }

        val buttonUpdate = view.findViewById<Button>(R.id.buttonUpdate)
        buttonUpdate.setOnClickListener {
            updateData()
        }

        updateData()

        updateTime()
        return view
    }

    private fun updateData() {
        strategy1728.process()
        adapterList.setData(strategy1728.resort())
    }

    private fun updateTime() {
        val time = strategy1728.strategyStartTime.time.toString("HH:mm:ss")
        val act = requireActivity() as AppCompatActivity
        act.supportActionBar?.title = time
    }

    inner class Item1728RecyclerViewAdapter(
        private var values: List<Stock>
    ) : RecyclerView.Adapter<Item1728RecyclerViewAdapter.ViewHolder>() {

        fun setData(newValues: List<Stock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(
                R.layout.fragment_1728_start_item,
                parent,
                false
            )

            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.stock = item

            holder.tickerView.text = "${position}. ${item.marketInstrument.ticker}"
            holder.priceView.text = item.getPrice1728String()

            val volume = item.getTodayVolume() / 1000f
            holder.volumeTodayView.text = "%.1fk".format(volume)

            holder.changePriceAbsoluteView.text = item.changePrice1728DayAbsolute.toDollar()
            holder.changePricePercentView.text = item.changePrice1728DayPercent.toPercent()

            if (item.changePrice1728DayAbsolute < 0) {
                holder.changePriceAbsoluteView.setTextColor(Utils.RED)
                holder.changePricePercentView.setTextColor(Utils.RED)
            } else {
                holder.changePriceAbsoluteView.setTextColor(Utils.GREEN)
                holder.changePricePercentView.setTextColor(Utils.GREEN)
            }

            holder.itemView.setOnClickListener {
                Utils.openTinkoffForTicker(requireContext(), holder.stock.marketInstrument.ticker)
            }

            holder.buttonBuy.setOnClickListener {
                if (SettingsManager.get1728PurchaseVolume() <= 0) {
                    Utils.showMessageAlert(requireContext(),"В настройках не задана сумма покупки для позиции, раздел 1728.")
                } else {
                    val purchase = PurchaseStock(holder.stock)

                    // считаем лоты
                    purchase.lots = (SettingsManager.get1728PurchaseVolume() / purchase.stock.getPriceDouble()).roundToInt()

                    purchase.buyLimitFromAsk(SettingsManager.get1728TakeProfit())
                }
            }

            if (SettingsManager.get1728QuickBuy()) {
                holder.buttonBuy.visibility = View.VISIBLE
            } else {
                holder.buttonBuy.visibility = View.GONE
            }
        }

        override fun getItemCount(): Int = values.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            lateinit var stock: Stock

            val tickerView: TextView = view.findViewById(R.id.stock_item_ticker)
            val priceView: TextView = view.findViewById(R.id.stock_item_price)

            val volumeTodayView: TextView = view.findViewById(R.id.stock_item_volume_today)

            val changePriceAbsoluteView: TextView = view.findViewById(R.id.stock_item_price_change_absolute)
            val changePricePercentView: TextView = view.findViewById(R.id.stock_item_price_change_percent)

            val buttonBuy: Button = view.findViewById(R.id.buttonBuy)
        }
    }
}