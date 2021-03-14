package com.project.ti2358.ui.strategyReports

import android.graphics.Color
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
import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.manager.StockManager
import com.project.ti2358.data.manager.StrategyReports
import com.project.ti2358.service.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class StrategyReportsStartFragment : Fragment() {
    val stockManager: StockManager by inject()
    val strategyReports: StrategyReports by inject()
    var adapterList: ItemReportsRecyclerViewAdapter = ItemReportsRecyclerViewAdapter(emptyList())
    lateinit var stocks: MutableList<Stock>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_reports, container, false)
        val list = view.findViewById<RecyclerView>(R.id.list)
        list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))

        if (list is RecyclerView) {
            with(list) {
                layoutManager = LinearLayoutManager(context)
                adapter = adapterList
            }
        }

        val buttonReports = view.findViewById<Button>(R.id.buttonReport)
        buttonReports.setOnClickListener {
            updateDataReport()
        }

        val buttonDivs = view.findViewById<Button>(R.id.buttonDiv)
        buttonDivs.setOnClickListener {
            updateDataDivs()
        }

        updateDataReport()

        GlobalScope.launch(Dispatchers.Main) {
            stockManager.reloadReports()
            updateDataReport()
        }
        return view
    }

    private fun updateDataReport() {
        stocks = strategyReports.process()
        stocks = strategyReports.resortReport()
        adapterList.setData(stocks)

        val act = requireActivity() as AppCompatActivity
        act.supportActionBar?.title = getString(R.string.menu_reports)
    }

    private fun updateDataDivs() {
        stocks = strategyReports.process()
        stocks = strategyReports.resortDivs()
        adapterList.setData(stocks)

        val act = requireActivity() as AppCompatActivity
        act.supportActionBar?.title = getString(R.string.menu_divindens)
    }

    inner class ItemReportsRecyclerViewAdapter(
        private var values: List<Stock>
    ) : RecyclerView.Adapter<ItemReportsRecyclerViewAdapter.ViewHolder>() {

        fun setData(newValues: List<Stock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(
                R.layout.fragment_reports_item,
                parent,
                false
            )

            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.stock = item

            holder.tickerView.text = "${position + 1}) ${item.instrument.ticker}"
            holder.priceView.text = "${item.getPrice2359String()} ➡ ${item.getPriceString()}"

            holder.changePriceAbsoluteView.text = item.changePrice2359DayAbsolute.toMoney(item)
            holder.changePricePercentView.text = item.changePrice2359DayPercent.toPercent()

            holder.changePriceAbsoluteView.setTextColor(Utils.getColorForValue(item.changePrice2359DayAbsolute))
            holder.changePricePercentView.setTextColor(Utils.getColorForValue(item.changePrice2359DayAbsolute))

            holder.itemView.setOnClickListener {
                Utils.openTinkoffForTicker(requireContext(), holder.stock.instrument.ticker)
            }

            holder.sectorView.text = item.getSectorName()
            holder.sectorView.setTextColor(item.closePrices?.sector?.getColor() ?: Color.BLACK)

            item.dividend?.let {
                val emoji = if (it.profit > 1.0) " 🤑" else ""
                holder.info1View.text = "+${it.profit}%$emoji"
                holder.info1View.setTextColor(Utils.GREEN)
                holder.info2View.visibility = View.GONE

                holder.dateView.text = it.date_format
                holder.dateView.setTextColor(Utils.RED)
            }

            item.report?.let {
                if (it.estimate_rev_per != null) {
                    if (it.estimate_rev_per > 0) {
                        holder.info1View.text = "REV: +${it.estimate_rev_per}$"
                    } else {
                        holder.info1View.text = "REV: ${it.estimate_rev_per}$"
                    }
                    holder.info1View.setTextColor(Utils.getColorForValue(it.estimate_rev_per))
                }

                if (it.estimate_eps != null) {
                    if (it.estimate_eps > 0) {
                        holder.info2View.text = "EPS: (+${it.estimate_eps})%"
                    } else {
                        holder.info2View.text = "EPS: (${it.estimate_eps})%"
                    }
                    holder.info2View.setTextColor(Utils.getColorForValue(it.estimate_eps))
                }

                if (it.actual_rev_per != null) {
                    if (it.actual_rev_per > 0) {
                        holder.info1View.text = "REV: +${it.actual_rev_per}$"
                    } else {
                        holder.info1View.text = "REV: ${it.actual_rev_per}$"
                    }
                    holder.info1View.setTextColor(Utils.getColorForValue(it.actual_rev_per))
                }

                if (it.actual_eps != null) {
                    if (it.actual_eps > 0) {
                        holder.info2View.text = "EPS: (+${it.actual_eps})%"
                    } else {
                        holder.info2View.text = "EPS: (${it.actual_eps})%"
                    }
                    holder.info2View.setTextColor(Utils.getColorForValue(it.actual_eps))
                }

                var tod = if (it.tod == "post") " 🌚" else " 🌞"
                if (it.actual_eps != null || it.actual_rev_per != null) {
                    tod += "✅"
                }
                holder.dateView.text = "${it.date_format} $tod"
                holder.dateView.setTextColor(Utils.RED)
            }
        }

        override fun getItemCount(): Int = values.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            lateinit var stock: Stock

            val tickerView: TextView = view.findViewById(R.id.stock_item_ticker)
            val priceView: TextView = view.findViewById(R.id.stock_item_price)

            val changePriceAbsoluteView: TextView = view.findViewById(R.id.stock_item_price_change_absolute)
            val changePricePercentView: TextView = view.findViewById(R.id.stock_item_price_change_percent)

            val sectorView: TextView = view.findViewById(R.id.stock_sector)
            val info1View: TextView = view.findViewById(R.id.stock_info_1)
            val info2View: TextView = view.findViewById(R.id.stock_info_2)

            val dateView: TextView = view.findViewById(R.id.stock_date)
        }
    }
}