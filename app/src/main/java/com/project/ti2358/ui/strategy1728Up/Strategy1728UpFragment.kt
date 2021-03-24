package com.project.ti2358.ui.strategy1728Up

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import kotlin.math.roundToInt

@KoinApiExtension
class Strategy1728UpFragment : Fragment() {
    val stockManager: StockManager by inject()
    val strategy1728Up: Strategy1728Up by inject()
    var adapterList: Item1728RecyclerViewAdapter = Item1728RecyclerViewAdapter(emptyList())

    var step1728: Step1728 = Step1728.step700to1200
    var stocks: MutableList<Stock> = mutableListOf()

    var job: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_1728_up, container, false)
        val list = view.findViewById<RecyclerView>(R.id.list)

        list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))

        if (list is RecyclerView) {
            with(list) {
                layoutManager = LinearLayoutManager(context)
                adapter = adapterList
            }
        }

        val buttonStep1 = view.findViewById<Button>(R.id.button_step_1)
        buttonStep1.setOnClickListener {
            updateData(Step1728.step700to1200)
        }

        val buttonStep2 = view.findViewById<Button>(R.id.button_step_2)
        buttonStep2.setOnClickListener {
            updateData(Step1728.step700to1530)
        }

        val buttonStep3 = view.findViewById<Button>(R.id.button_step_3)
        buttonStep3.setOnClickListener {
            updateData(Step1728.step1630to1635)
        }

        val buttonStepGo = view.findViewById<Button>(R.id.button_step_go)
        buttonStepGo.setOnClickListener {
            updateData(Step1728.stepFinal)
        }

        job?.cancel()
        job = GlobalScope.launch(Dispatchers.Main) {
            stockManager.reloadStockPrice1728()
            updateData(step1728)
        }

        updateData(Step1728.step700to1200)
        return view
    }

    private fun updateData(localStep1728: Step1728) {
        step1728 = localStep1728
        stocks = when (step1728) {
            Step1728.step700to1200 -> strategy1728Up.process700to1200()
            Step1728.step700to1530 -> strategy1728Up.process700to1600()
            Step1728.step1630to1635 -> strategy1728Up.process1630to1635()
            Step1728.stepFinal -> strategy1728Up.processFinal()
        }
        adapterList.setData(stocks)
        updateTitle()
    }

    private fun updateTitle() {
        val title = when (step1728) {
            Step1728.step700to1200 -> "1: 07:00 - 12:00, объём ${SettingsManager.get1728Volume(0)}"
            Step1728.step700to1530 -> "2: 07:00 - 16:00, объём ${SettingsManager.get1728Volume(1)}"
            Step1728.step1630to1635 -> "3: 16:30 - 16:35, объём ${SettingsManager.get1728Volume(2)}"
            Step1728.stepFinal -> "1 - 2 - 3 - 🚀"
        }
        val act = requireActivity() as AppCompatActivity
        act.supportActionBar?.title = title
    }

    inner class Item1728RecyclerViewAdapter(
        private var values: List<Stock>
    ) : RecyclerView.Adapter<Item1728RecyclerViewAdapter.ViewHolder>() {

        fun setData(newValues: List<Stock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.fragment_1728_item_up, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.stock = item

            holder.tickerView.text = "${position + 1}) ${item.instrument.ticker}"
            holder.priceView.text = item.getPriceDouble().toMoney(item)

            val changePercent = when (step1728) {
                Step1728.step700to1200 -> item.changePrice700to1200Percent
                Step1728.step700to1530 -> item.changePrice700to1600Percent
                Step1728.step1630to1635 -> item.changePrice1630to1635Percent
                Step1728.stepFinal -> item.changePrice1630to1635Percent
            }

            val changeAbsolute = when (step1728) {
                Step1728.step700to1200 -> item.changePrice700to1200Absolute
                Step1728.step700to1530 -> item.changePrice700to1600Absolute
                Step1728.step1630to1635 -> item.changePrice1630to1635Absolute
                Step1728.stepFinal -> item.changePrice1630to1635Absolute
            }

            val volume = when (step1728) {
                Step1728.step700to1200 -> item.volume700to1200
                Step1728.step700to1530 -> item.volume700to1600
                Step1728.step1630to1635 -> item.volume1630to1635
                Step1728.stepFinal -> item.getTodayVolume()
            } / 1000f

            holder.volumeTodayView.text = "%.1fk".format(volume)

            holder.changePriceAbsoluteView.text = changeAbsolute.toMoney(item)
            holder.changePricePercentView.text = changePercent.toPercent()

            holder.changePriceAbsoluteView.setTextColor(Utils.getColorForValue(changePercent))
            holder.changePricePercentView.setTextColor(Utils.getColorForValue(changePercent))

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

                    // включаем трейлинг тейк
                    if (SettingsManager.get1728TrailingStop()) {
                        purchase.trailingStop = true
                        purchase.trailingStopTakeProfitPercentActivation = SettingsManager.getTrailingStopTakeProfitPercentActivation()
                        purchase.trailingStopTakeProfitPercentDelta = SettingsManager.getTrailingStopTakeProfitPercentDelta()
                        purchase.trailingStopStopLossPercent = SettingsManager.getTrailingStopStopLossPercent()
                    }

                    purchase.buyFromAsk1728()
                }
            }

            if (step1728 == Step1728.stepFinal || step1728 == Step1728.step1630to1635) {
                holder.buttonBuy.visibility = View.VISIBLE
            } else {
                holder.buttonBuy.visibility = View.GONE
            }
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