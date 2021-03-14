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
import com.project.ti2358.TheApplication
import com.project.ti2358.data.manager.PurchaseStock
import com.project.ti2358.data.manager.Strategy1000Buy
import com.project.ti2358.data.manager.SettingsManager
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class Strategy1000BuyFinishFragment : Fragment() {

    val strategy1000Buy: Strategy1000Buy by inject()
    var adapterList: Item1005RecyclerViewAdapter = Item1005RecyclerViewAdapter(emptyList())
    var positions: MutableList<PurchaseStock> = mutableListOf()
    var infoTextView: TextView? = null
    var buttonStart700: Button? = null
    var buttonStart1000: Button? = null

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

        ///////////////////////////////////////////////////////////////
        buttonStart700 = view.findViewById(R.id.buttonStart700)
        buttonStart700?.setOnClickListener {
            if (SettingsManager.get1000BuyPurchaseVolume() <= 0) {
                Utils.showMessageAlert(requireContext(), "В настройках не задана общая сумма покупки, раздел 1000 buy.")
            } else {
                if (Utils.isServiceRunning(requireContext(), Strategy700BuyService::class.java)) {
                    requireContext().stopService(Intent(context, Strategy700BuyService::class.java))
                } else {
                    if (strategy1000Buy.getTotalPurchasePieces() > 0) {
                        Utils.startService(requireContext(), Strategy700BuyService::class.java)
                    }
                }
            }
            updateServiceButtonText700()
        }
        updateServiceButtonText700()
        ///////////////////////////////////////////////////////////////
        ///////////////////////////////////////////////////////////////
        buttonStart1000 = view.findViewById(R.id.buttonStart1000)
        buttonStart1000?.setOnClickListener {
            if (SettingsManager.get1000BuyPurchaseVolume() <= 0) {
                Utils.showMessageAlert(requireContext(), "В настройках не задана общая сумма покупки, раздел 1000 buy.")
            } else {
                if (Utils.isServiceRunning(requireContext(), Strategy1000BuyService::class.java)) {
                    requireContext().stopService(Intent(context, Strategy1000BuyService::class.java))
                } else {
                    if (strategy1000Buy.getTotalPurchasePieces() > 0) {
                        Utils.startService(requireContext(), Strategy1000BuyService::class.java)
                    }
                }
            }
            updateServiceButtonText1000()
        }
        updateServiceButtonText1000()
        ///////////////////////////////////////////////////////////////

        positions = strategy1000Buy.getPurchaseStock()
        adapterList.setData(positions)

        infoTextView = view.findViewById(R.id.info_text)
        updateInfoText()

        return view
    }

    fun updateInfoText() {
        val time = "07:00:00.100ms или 10:00:00.100ms"

        val prepareText: String = TheApplication.application.applicationContext.getString(R.string.prepare_start_1000_buy_text)
        infoTextView?.text = String.format(
            prepareText,
            time,
            positions.size,
            strategy1000Buy.getTotalPurchaseString(strategy1000Buy.stocksToBuy)
        )
    }

    private fun updateServiceButtonText700() {
        if (Utils.isServiceRunning(requireContext(), Strategy700BuyService::class.java)) {
            buttonStart700?.text = getString(R.string.stop_sell_700)
        } else {
            buttonStart700?.text = getString(R.string.start_sell_700)
        }
    }

    private fun updateServiceButtonText1000() {
        if (Utils.isServiceRunning(requireContext(), Strategy1000BuyService::class.java)) {
            buttonStart1000?.text = getString(R.string.stop_sell_1000)
        } else {
            buttonStart1000?.text = getString(R.string.start_sell_1000)
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
            holder.tickerView.text = "${item.stock.instrument.ticker} x ${item.lots}"
            holder.currentPriceView.text = "${item.stock.getPrice2359String()} ➡ ${avg}$"
            holder.totalPriceView.text = (item.stock.getPriceDouble() * item.lots).toMoney(item.stock)

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
            holder.priceChangeAbsoluteView.text = item.absoluteLimitPriceChange.toMoney(item.stock)
            holder.priceChangeAbsoluteTotalView.text = (item.absoluteLimitPriceChange * item.lots).toMoney(item.stock)

            holder.priceBuyView.text = item.getLimitPriceDouble().toMoney(item.stock)
            holder.priceBuyTotalView.text = (item.getLimitPriceDouble() * item.lots).toMoney(item.stock)

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