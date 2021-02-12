package com.project.ti2358.ui.strategy1000Buy

import android.content.Intent
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
import com.project.ti2358.data.manager.PurchaseStock
import com.project.ti2358.data.manager.Strategy1000Buy
import com.project.ti2358.data.service.SettingsManager
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class Strategy1000BuyFinishFragment : Fragment() {

    val strategy1000Buy: Strategy1000Buy by inject()
    var adapterList: Item1005RecyclerViewAdapter = Item1005RecyclerViewAdapter(emptyList())
    var infoTextView: TextView? = null
    var buttonStart: Button? = null
    var positions: MutableList<PurchaseStock> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_1000_buy_finish, container, false)
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

        buttonStart = view.findViewById<Button>(R.id.buttonStart)
        updateServiceButtonText()

        buttonStart?.setOnClickListener {
            if (SettingsManager.get1000BuyPurchaseVolume() <= 0) {
                Utils.showMessageAlert(requireContext(), "В настройках не задана общая сумма покупки, раздел 1000 buy.")
            } else {
                if (Utils.isServiceRunning(requireContext(), Strategy1000BuyService::class.java)) {
                    requireContext().stopService(
                        Intent(
                            context,
                            Strategy1000BuyService::class.java
                        )
                    )
                } else {
                    if (strategy1000Buy.getTotalPurchasePieces() > 0) {
                        Utils.startService(requireContext(), Strategy1000BuyService::class.java)
                    }
                }
            }
            updateServiceButtonText()
        }

        positions = strategy1000Buy.getPurchaseStock()
        adapterList.setData(positions)

        infoTextView = view.findViewById<TextView>(R.id.info_text)
        updateInfoText()

        return view
    }

    fun updateInfoText() {
        val time = "10:00:01"

        val prepareText: String = SettingsManager.context.getString(R.string.prepare_start_1000_buy_text)
        infoTextView?.text = String.format(
            prepareText,
            time,
            positions.size,
            strategy1000Buy.getTotalPurchaseString()
        )
    }

    private fun updateServiceButtonText() {
        if (Utils.isServiceRunning(requireContext(), Strategy1000BuyService::class.java)) {
            buttonStart?.text = getString(R.string.service_2358_stop)
        } else {
            buttonStart?.text = getString(R.string.service_2358_start)
        }
    }

    inner class Item1005RecyclerViewAdapter(
        private var values: List<PurchaseStock>
    ) : RecyclerView.Adapter<Item1005RecyclerViewAdapter.ViewHolder>() {

        fun setData(newValues: List<PurchaseStock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fragment_1000_buy_finish_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.position = item

            val avg = item.stock.getPriceDouble()
            holder.tickerView.text = "${item.stock.marketInstrument.ticker} ${item.lots} шт."
            holder.currentPriceView.text = "${item.stock.getPrice2359String()} -> ${avg} $"
            holder.totalPriceView.text = "%.2f $".format(item.stock.getPriceDouble() * item.lots)

            refreshPercent(holder)

            holder.buttonPlus.setOnClickListener {
                item.addPriceLimitPercent(0.05)
                refreshPercent(holder)
                updateInfoText()
            }

            holder.buttonMinus.setOnClickListener {
                item.addPriceLimitPercent(-0.05)
                refreshPercent(holder)
                updateInfoText()
            }
        }

        fun refreshPercent(holder: ViewHolder) {
            val item = holder.position
            val percent = item.percentLimitPriceChange

            holder.priceChangePercentView.text = percent.toPercent()
            holder.priceChangeAbsoluteView.text = item.absoluteLimitPriceChange.toDollar()
            holder.priceChangeAbsoluteTotalView.text = (item.absoluteLimitPriceChange * item.lots).toDollar()

            holder.priceBuyView.text = item.getLimitPriceDouble().toDollar()
            holder.priceBuyTotalView.text = (item.getLimitPriceDouble() * item.lots).toDollar()

            if (percent < 0) {
                holder.priceChangePercentView.setTextColor(Utils.RED)
                holder.priceChangeAbsoluteView.setTextColor(Utils.RED)
                holder.priceChangeAbsoluteTotalView.setTextColor(Utils.RED)
            } else {
                holder.priceChangePercentView.setTextColor(Utils.GREEN)
                holder.priceChangeAbsoluteView.setTextColor(Utils.GREEN)
                holder.priceChangeAbsoluteTotalView.setTextColor(Utils.GREEN)
            }
        }

        override fun getItemCount(): Int = values.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            lateinit var position: PurchaseStock

            val tickerView: TextView = view.findViewById(R.id.stock_item_ticker)
            val currentPriceView: TextView = view.findViewById(R.id.stock_item_price)
            val totalPriceView: TextView = view.findViewById(R.id.stock_total_price)

            val priceBuyView: TextView = view.findViewById(R.id.stock_item_price_buy)
            val priceBuyTotalView: TextView = view.findViewById(R.id.stock_item_price_total_buy)

            val priceChangePercentView: TextView = view.findViewById(R.id.stock_price_change_percent)
            val priceChangeAbsoluteView: TextView = view.findViewById(R.id.stock_price_absolute_change)
            val priceChangeAbsoluteTotalView: TextView = view.findViewById(R.id.stock_price_absolute_total_change)

            val buttonPlus: Button = view.findViewById(R.id.buttonPlus)
            val buttonMinus: Button = view.findViewById(R.id.buttonMinus)
        }
    }
}