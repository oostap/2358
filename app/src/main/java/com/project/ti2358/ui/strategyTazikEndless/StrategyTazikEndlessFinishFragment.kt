package com.project.ti2358.ui.strategyTazikEndless

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
import com.project.ti2358.data.manager.SettingsManager
import com.project.ti2358.data.manager.StrategyTazikEndless
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class StrategyTazikEndlessFinishFragment : Fragment() {

    val strategyTazikEndless: StrategyTazikEndless by inject()
    var adapterList: ItemTazikRecyclerViewAdapter = ItemTazikRecyclerViewAdapter(emptyList())
    var infoTextView: TextView? = null
    var buttonStartNow: Button? = null
    var buttonStartLater: Button? = null
    var positions: MutableList<PurchaseStock> = mutableListOf()
    var startTime: String = ""
    var scheduledStart: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tazik_finish, container, false)
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

        buttonStartNow = view.findViewById(R.id.buttonStartNow)
        buttonStartNow?.setOnClickListener {
            tryStartTazik(false)
        }

        buttonStartLater = view.findViewById(R.id.buttonStartSchedule)
        buttonStartLater?.setOnClickListener {
            tryStartTazik(true)
        }

        positions = strategyTazikEndless.getPurchaseStock()
        adapterList.setData(positions)

        infoTextView = view.findViewById(R.id.info_text)

        updateInfoText()
        updateServiceButtonText()

        return view
    }

    fun tryStartTazik(scheduled : Boolean) {
        if (SettingsManager.getTazikPurchaseVolume() <= 0 || SettingsManager.getTazikPurchaseParts() == 0) {
            Utils.showMessageAlert(requireContext(),"В настройках не задана общая сумма покупки или количество частей, раздел Автотазик.")
        } else {
            if (Utils.isServiceRunning(requireContext(), StrategyTazikService::class.java)) {
                requireContext().stopService(Intent(context, StrategyTazikService::class.java))
                strategyTazikEndless.stopStrategy()
            } else {
                if (strategyTazikEndless.stocksToPurchase.size > 0) {
                    Utils.startService(requireContext(), StrategyTazikService::class.java)
                    strategyTazikEndless.prepareStrategy(scheduled, startTime)
                }
            }
        }
        scheduledStart = scheduled
        updateServiceButtonText()
    }

    fun updateInfoText() {
        val percent = SettingsManager.getTazikChangePercent()
        val volume = SettingsManager.getTazikPurchaseVolume().toDouble()
        val p = SettingsManager.getTazikPurchaseParts()
        val parts = "%d по %.2f$".format(p, volume / p)
        startTime = SettingsManager.getTazikNearestTime()

        val prepareText: String = TheApplication.application.applicationContext.getString(R.string.prepare_start_tazik_buy_text)
        infoTextView?.text = String.format(
            prepareText,
            positions.size,
            percent,
            volume,
            parts,
            startTime
        )
    }

    private fun updateServiceButtonText() {
        if (Utils.isServiceRunning(requireContext(), StrategyTazikService::class.java)) {
            if (scheduledStart) {
                buttonStartLater?.text = getString(R.string.stop)
            } else {
                buttonStartNow?.text = getString(R.string.stop)
            }
        } else {
            if (scheduledStart) {
                buttonStartLater?.text = getString(R.string.stop)
            } else {
                buttonStartNow?.text = getString(R.string.start_now)
            }
        }
    }

    inner class ItemTazikRecyclerViewAdapter(
        private var values: List<PurchaseStock>
    ) : RecyclerView.Adapter<ItemTazikRecyclerViewAdapter.ViewHolder>() {

        fun setData(newValues: List<PurchaseStock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fragment_tazik_finish_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.position = item

            val avg = item.stock.getPriceDouble()
            holder.tickerView.text = "${position + 1}) ${item.stock.instrument.ticker} x ${item.lots}"
            holder.currentPriceView.text = "${item.stock.getPrice2359String()} ➡ ${avg.toMoney(item.stock)}"
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

            holder.itemView.setBackgroundColor(Utils.getColorForIndex(position))
        }

        fun refreshPercent(holder: ViewHolder) {
            val item = holder.position
            val percent = item.percentLimitPriceChange

            holder.priceChangePercentView.text = percent.toPercent()
            holder.priceChangeAbsoluteView.text = item.absoluteLimitPriceChange.toMoney(item.stock)
            holder.priceChangeAbsoluteTotalView.text = (item.absoluteLimitPriceChange * item.lots).toMoney(item.stock)

            holder.priceBuyView.text = item.getLimitPriceDouble().toMoney(item.stock)
            holder.priceBuyTotalView.text = (item.getLimitPriceDouble() * item.lots).toMoney(item.stock)

            holder.priceChangePercentView.setTextColor(Utils.getColorForValue(percent))
            holder.priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(percent))
            holder.priceChangeAbsoluteTotalView.setTextColor(Utils.getColorForValue(percent))
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