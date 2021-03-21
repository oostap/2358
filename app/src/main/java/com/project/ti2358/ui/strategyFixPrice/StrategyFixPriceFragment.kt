package com.project.ti2358.ui.strategyFixPrice

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
import com.project.ti2358.data.manager.*
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import kotlin.math.roundToInt

@KoinApiExtension
class StrategyFixPriceFragment : Fragment() {

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

        val buttonReset = view.findViewById<Button>(R.id.buttonReset)
        buttonReset.setOnClickListener { // сброс времени отслеживания
            strategyFixPrice.resetStrategy()
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
        val time = StrategyFixPrice.strategyStartTime.time.toString("HH:mm:ss")
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
            val view = LayoutInflater.from(parent.context).inflate(
                R.layout.fragment_fixprice_item,
                parent,
                false
            )

            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.stock = item

            holder.tickerView.text = "${position + 1}) ${item.instrument.ticker}"
            holder.priceView.text = item.getPriceFixPriceString()

            val volume = item.getTodayVolume() / 1000f
            holder.volumeTodayView.text = "%.1fk".format(volume)

            holder.changePriceAbsoluteView.text = item.changePriceFixDayAbsolute.toMoney(item)
            holder.changePricePercentView.text = item.changePriceFixDayPercent.toPercent()

            holder.changePriceAbsoluteView.setTextColor(Utils.getColorForValue(item.changePriceFixDayAbsolute))
            holder.changePricePercentView.setTextColor(Utils.getColorForValue(item.changePriceFixDayAbsolute))

            holder.itemView.setOnClickListener {
                Utils.openTinkoffForTicker(requireContext(), holder.stock.instrument.ticker)
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

            holder.buttonBuy.visibility = View.VISIBLE
            holder.itemView.setBackgroundColor(Utils.getColorForIndex(position))
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