package com.project.ti2358.ui.strategy1000

import android.app.ActivityManager
import android.content.Context.ACTIVITY_SERVICE
import android.content.Intent
import android.graphics.Color
import android.os.Build
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
import com.project.ti2358.data.manager.PurchasePosition
import com.project.ti2358.data.model.dto.PortfolioPosition
import com.project.ti2358.data.service.SettingsManager
import com.project.ti2358.data.service.Strategy1000
import com.project.ti2358.data.service.Strategy1728
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension


@KoinApiExtension
class Strategy1000FinishFragment : Fragment() {

    val strategy1000: Strategy1000 by inject()
    var adapterList: Item1000RecyclerViewAdapter = Item1000RecyclerViewAdapter(emptyList())
    var infoTextView: TextView? = null
    var positions: MutableList<PurchasePosition> = mutableListOf()
    var buttonStart: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_1000_finish, container, false)
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
            if (Utils.isServiceRunning(requireContext(), Strategy1000Service::class.java)) {
                requireContext().stopService(Intent(context, Strategy1000Service::class.java))
            } else {
                if (strategy1000.getTotalPurchasePieces() > 0) {
                    Utils.startService(requireContext(), Strategy1000Service::class.java)
                }
            }
            updateServiceButtonText()
        }

        positions = strategy1000.getSellPosition()
        adapterList.setData(positions)

        infoTextView = view.findViewById<TextView>(R.id.info_text)
        updateInfoText()

        return view
    }

    private fun updateServiceButtonText() {
        if (Utils.isServiceRunning(requireContext(), Strategy1000Service::class.java)) {
            buttonStart?.text = getString(R.string.service_2358_stop)
        } else {
            buttonStart?.text = getString(R.string.service_2358_start)
        }
    }

    fun updateInfoText() {
        var time = "10:00:01"

        val prepareText: String = SettingsManager.context.getString(R.string.prepare_start_1000_text)
        infoTextView?.text = String.format(
            prepareText,
            time,
            positions.size,
            strategy1000.getTotalSellString()
        )
    }

    inner class Item1000RecyclerViewAdapter(
        private var values: List<PurchasePosition>
    ) : RecyclerView.Adapter<Item1000RecyclerViewAdapter.ViewHolder>() {

        fun setData(newValues: List<PurchasePosition>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fragment_1000_finish_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.position = item

            val avg = item.position.getAveragePrice()
            holder.tickerView.text = "${item.position.ticker} ${item.position.lots} шт."
            holder.currentPriceView.text = "${avg} $"

            var totalCash = item.position.balance * avg
            var percent = (100 * item.position.expectedYield.value) / totalCash
            holder.currentProfitView.text = "%.2f".format(percent) + "%"

            var change = item.position.expectedYield?.value
            totalCash += change
            holder.totalPriceView.text = "%.2f $".format(totalCash)

            holder.totalPriceProfitView.text = "${change} $"
            holder.priceProfitView.text = "${change / item.position.lots} $"

            if (percent < 0) {
                holder.currentProfitView.setTextColor(Utils.RED)
                holder.priceProfitView.setTextColor(Utils.RED)
                holder.totalPriceProfitView.setTextColor(Utils.RED)
            } else {
                holder.currentProfitView.setTextColor(Utils.GREEN)
                holder.priceProfitView.setTextColor(Utils.GREEN)
                holder.totalPriceProfitView.setTextColor(Utils.GREEN)
            }

            refreshFuturePercent(holder)

            holder.buttonPlus.setOnClickListener {
                item.profit += 0.05
                refreshFuturePercent(holder)
                updateInfoText()
            }

            holder.buttonMinus.setOnClickListener {
                item.profit += -0.05
                refreshFuturePercent(holder)
                updateInfoText()
            }
        }

        fun refreshFuturePercent(holder: ViewHolder) {
            val item = holder.position
            var futurePercent = item.profit
            holder.futureProfitView.text = "%.2f ".format(futurePercent) + "%"

            val avg = item.position.getAveragePrice()
            var futureProfitPrice = item.getProfitPrice() - avg
            holder.futureProfitPriceView.text = "%.2f $".format(futureProfitPrice)
            holder.totalFutureProfitPriceView.text = "%.2f $".format(futureProfitPrice * item.position.balance)

            if (futureProfitPrice < 0) {
                holder.futureProfitView.setTextColor(Utils.RED)
                holder.futureProfitPriceView.setTextColor(Utils.RED)
                holder.totalFutureProfitPriceView.setTextColor(Utils.RED)
            } else {
                holder.futureProfitView.setTextColor(Utils.GREEN)
                holder.futureProfitPriceView.setTextColor(Utils.GREEN)
                holder.totalFutureProfitPriceView.setTextColor(Utils.GREEN)
            }
        }

        override fun getItemCount(): Int = values.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            lateinit var position: PurchasePosition

            val tickerView: TextView = view.findViewById(R.id.stock_item_ticker)
            val currentPriceView: TextView = view.findViewById(R.id.stock_item_price)
            val totalPriceView: TextView = view.findViewById(R.id.stock_total_price)

            val priceProfitView: TextView = view.findViewById(R.id.stock_item_price_profit)
            val totalPriceProfitView: TextView = view.findViewById(R.id.stock_total_price_profit)

            val currentProfitView: TextView = view.findViewById(R.id.stock_current_change)

            val futureProfitPriceView: TextView = view.findViewById(R.id.stock_profit_price_change)
            val totalFutureProfitPriceView: TextView = view.findViewById(R.id.stock_profit_total_price_change)

            val futureProfitView: TextView = view.findViewById(R.id.stock_profit_percent)

            val buttonPlus: Button = view.findViewById(R.id.buttonPlus)
            val buttonMinus: Button = view.findViewById(R.id.buttonMinus)

            override fun toString(): String {
                return super.toString() + " '" + tickerView.text + "'"
            }
        }
    }
}