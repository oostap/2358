package com.project.ti2358.ui.strategy2358

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.TheApplication
import com.project.ti2358.data.manager.PurchaseStock
import com.project.ti2358.data.manager.Strategy2358
import com.project.ti2358.data.manager.SettingsManager
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class Strategy2358FinishFragment : Fragment() {

    val strategy2358: Strategy2358 by inject()
    var adapterList: Item2358RecyclerViewAdapter = Item2358RecyclerViewAdapter(emptyList())
    var buttonStart: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_2358_finish, container, false)
        val list = view.findViewById<RecyclerView>(R.id.list)

        list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))

        if (list is RecyclerView) {
            with(list) {
                layoutManager = LinearLayoutManager(context)
                adapter = adapterList
            }
        }

        buttonStart = view.findViewById<Button>(R.id.buttonStart)
        updateServiceButtonText()

        buttonStart?.setOnClickListener {
            if (SettingsManager.get2358PurchaseVolume() <= 0) {
                Utils.showMessageAlert(requireContext(), "В настройках не задана общая сумма покупки, раздел 2358.")
            } else {
                if (Utils.isServiceRunning(requireContext(), Strategy2358Service::class.java)) {
                    requireContext().stopService(Intent(context, Strategy2358Service::class.java))
                } else {
                    if (strategy2358.getTotalPurchasePieces() > 0) {
                        Utils.startService(requireContext(), Strategy2358Service::class.java)
                    }
                }
            }
            updateServiceButtonText()
        }

        var stocks = strategy2358.getPurchaseStock(true)
        adapterList.setData(stocks)

        var time = SettingsManager.get2358PurchaseTime()

        val textInfo = view.findViewById<TextView>(R.id.info_text)
        val prepareText: String = TheApplication.application.applicationContext.getString(R.string.prepare_start_2358_text)
        textInfo.text = String.format(
            prepareText,
            time,
            stocks.size,
            strategy2358.getTotalPurchaseString()
        )

        return view
    }

    private fun updateServiceButtonText() {
        if (Utils.isServiceRunning(requireContext(), Strategy2358Service::class.java)) {
            buttonStart?.text = getString(R.string.service_2358_stop)
        } else {
            buttonStart?.text = getString(R.string.service_2358_start)
        }
    }

    inner class Item2358RecyclerViewAdapter(
        private var values: List<PurchaseStock>
    ) : RecyclerView.Adapter<Item2358RecyclerViewAdapter.ViewHolder>() {

        fun setData(newValues: List<PurchaseStock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.fragment_2358_finish_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.purchaseStock = item

            holder.tickerView.text = "${position + 1}) ${item.stock.instrument.ticker}"
            holder.priceView.text = item.stock.getPriceString()

            holder.purchaseLotsView.text = "${item.lots} шт."
            holder.purchasePriceView.text = item.getPriceString()

            refreshPercent(holder, 0.0)
            holder.buttonPlus.setOnClickListener {
                refreshPercent(holder, 0.05)
            }

            holder.buttonMinus.setOnClickListener {
                refreshPercent(holder, -0.05)
            }

            holder.checkBoxView.setOnCheckedChangeListener { _, checked ->
                holder.purchaseStock.trailingStop = checked
                refreshPercent(holder, 0.0)
            }

            holder.itemView.setBackgroundColor(Utils.getColorForIndex(position))
        }

        private fun refreshPercent(holder: ViewHolder, delta: Double) {
            holder.checkBoxView.isChecked = holder.purchaseStock.trailingStop

            if (holder.purchaseStock.trailingStop) {
                holder.purchaseStock.addPriceProfit2358TrailingTakeProfit(delta)
                holder.profitPriceFromView.text = holder.purchaseStock.trailingStopTakeProfitPercentActivation.toPercent()
                holder.profitPriceToView.text = holder.purchaseStock.trailingStopTakeProfitPercentDelta.toPercent()
            } else {
                holder.purchaseStock.addPriceProfit2358Percent(delta)
                holder.profitPriceFromView.text = holder.purchaseStock.percentProfitSellFrom.toPercent()
                holder.profitPriceToView.text = holder.purchaseStock.percentProfitSellTo.toPercent()
            }
        }

        override fun getItemCount(): Int = values.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            lateinit var purchaseStock: PurchaseStock

            val tickerView: TextView = view.findViewById(R.id.stock_item_ticker)
            val priceView: TextView = view.findViewById(R.id.stock_item_price)

            val purchaseLotsView: TextView = view.findViewById(R.id.stock_purchase_lots)
            val purchasePriceView: TextView = view.findViewById(R.id.stock_purchase_price)

            val profitPriceFromView: TextView = view.findViewById(R.id.stock_price_profit_percent_from)
            val profitPriceToView: TextView = view.findViewById(R.id.stock_price_profit_percent_to)

            val buttonPlus: Button = view.findViewById(R.id.buttonPlus)
            val buttonMinus: Button = view.findViewById(R.id.buttonMinus)

            val checkBoxView: CheckBox = view.findViewById(R.id.check_box)
        }
    }
}