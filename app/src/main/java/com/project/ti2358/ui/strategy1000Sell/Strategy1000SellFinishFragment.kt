package com.project.ti2358.ui.strategy1000Sell

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.PurchaseStock
import com.project.ti2358.data.manager.Strategy1000Sell
import com.project.ti2358.data.service.SettingsManager
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class Strategy1000SellFinishFragment : Fragment() {

    private val strategy1000Sell: Strategy1000Sell by inject()
    var adapterList: Item1000RecyclerViewAdapter = Item1000RecyclerViewAdapter(emptyList())
    var infoTextView: TextView? = null
    var positions: MutableList<PurchaseStock> = mutableListOf()
    var buttonStart700: Button? = null
    var buttonStart1000: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_1000_sell_finish, container, false)
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

        ///////////////////////
        buttonStart1000 = view.findViewById(R.id.buttonStart1000)
        buttonStart1000?.setOnClickListener {
            if (Utils.isServiceRunning(requireContext(), Strategy1000SellService::class.java)) {
                requireContext().stopService(Intent(context, Strategy1000SellService::class.java))
            } else {
                if (strategy1000Sell.getTotalPurchasePieces() > 0) {
                    Utils.startService(requireContext(), Strategy1000SellService::class.java)
                }
            }

            this.findNavController().navigateUp()
            updateServiceButtonText1000()
        }
        updateServiceButtonText1000()
        //////////////////////
        buttonStart700 = view.findViewById(R.id.buttonStart700)
        buttonStart700?.setOnClickListener {
            if (Utils.isServiceRunning(requireContext(), Strategy700SellService::class.java)) {
                requireContext().stopService(Intent(context, Strategy700SellService::class.java))
            } else {
                if (strategy1000Sell.getTotalPurchasePieces() > 0) {
                    Utils.startService(requireContext(), Strategy700SellService::class.java)
                }
            }

            this.findNavController().navigateUp()
            updateServiceButtonText700()
        }
        updateServiceButtonText700()

        positions = strategy1000Sell.processSellPosition()
        adapterList.setData(positions)

        infoTextView = view.findViewById(R.id.info_text)
        updateInfoText()

        return view
    }

    private fun updateServiceButtonText1000() {
        if (Utils.isServiceRunning(requireContext(), Strategy1000SellService::class.java)) {
            buttonStart1000?.text = getString(R.string.stop_sell_1000)
        } else {
            buttonStart1000?.text = getString(R.string.start_sell_1000)
        }
    }

    private fun updateServiceButtonText700() {
        if (Utils.isServiceRunning(requireContext(), Strategy700SellService::class.java)) {
            buttonStart700?.text = getString(R.string.stop_sell_700)
        } else {
            buttonStart700?.text = getString(R.string.start_sell_700)
        }
    }

    fun updateInfoText() {
        val time = "07:00:01 или 10:00:01"
        val prepareText: String =
            SettingsManager.context.getString(R.string.prepare_start_1000_sell_text)
        infoTextView?.text = String.format(
            prepareText,
            time,
            positions.size,
            strategy1000Sell.getTotalSellString()
        )
    }

    inner class Item1000RecyclerViewAdapter(
        private var values: List<PurchaseStock>
    ) : RecyclerView.Adapter<Item1000RecyclerViewAdapter.ViewHolder>() {

        fun setData(newValues: List<PurchaseStock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fragment_1000_sell_finish_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.stock = item

            val avg = item.position.getAveragePrice()
            holder.tickerView.text = "${item.position.ticker} x ${item.position.lots}"

            val profit = item.position.getProfitAmount()
            var totalCash = item.position.balance * avg
            val percent = item.position.getProfitPercent()
            holder.currentProfitView.text = percent.toPercent()

            totalCash += profit
            holder.currentPriceView.text = "%.2f\$ ➡ %.2f\$".format(avg, totalCash)

            holder.totalPriceProfitView.text = profit.toDollar()
            holder.priceProfitView.text = (profit / item.position.lots).toDollar()

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
                item.percentProfitSellFrom += 0.05
                refreshFuturePercent(holder)
                updateInfoText()
            }

            holder.buttonMinus.setOnClickListener {
                item.percentProfitSellFrom += -0.05
                refreshFuturePercent(holder)
                updateInfoText()
            }
        }

        fun refreshFuturePercent(holder: ViewHolder) {
            val item = holder.stock
            val futurePercent = item.percentProfitSellFrom
            holder.futureProfitView.text = futurePercent.toPercent()

            val avg = item.position.getAveragePrice()
            val futureProfitPrice = item.getProfitPriceForSell() - avg
            holder.futureProfitPriceView.text = futureProfitPrice.toDollar()
            holder.totalFutureProfitPriceView.text = (futureProfitPrice * item.position.balance).toDollar()

            holder.totalPriceView.text = item.getProfitPriceForSell().toDollar()

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
            lateinit var stock: PurchaseStock

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
        }
    }
}