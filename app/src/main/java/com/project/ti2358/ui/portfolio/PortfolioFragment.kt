package com.project.ti2358.ui.portfolio

import android.content.pm.PackageInfo
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.TheApplication
import com.project.ti2358.data.manager.DepositManager
import com.project.ti2358.data.manager.OrderbookManager
import com.project.ti2358.data.manager.SettingsManager
import com.project.ti2358.data.model.dto.PortfolioPosition
import com.project.ti2358.data.service.ThirdPartyService
import com.project.ti2358.service.Utils
import com.project.ti2358.service.toMoney
import com.project.ti2358.service.toPercent
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.lang.Exception
import kotlin.math.sign

@KoinApiExtension
class PortfolioFragment : Fragment() {
    private val orderbookManager: OrderbookManager by inject()
    private val thirdPartyService: ThirdPartyService by inject()
    val depositManager: DepositManager by inject()
    var adapterList: ItemPortfolioRecyclerViewAdapter = ItemPortfolioRecyclerViewAdapter(emptyList())

    var jobUpdate: Job? = null
    var jobVersion: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        jobUpdate?.cancel()
        jobVersion?.cancel()

        super.onDestroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_portfolio, container, false)
        val list = view.findViewById<RecyclerView>(R.id.list)

        list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))

        if (list is RecyclerView) {
            with(list) {
                layoutManager = LinearLayoutManager(context)
                adapter = adapterList
            }
        }

        val buttonUpdate = view.findViewById<Button>(R.id.buttonUpdate)
        buttonUpdate.setOnClickListener {
            updateData()
        }

        val buttonOrder = view.findViewById<Button>(R.id.buttonOrders)
        buttonOrder.setOnClickListener {
            view.findNavController().navigate(R.id.action_nav_portfolio_to_nav_orders)
        }

        updateData()

        jobVersion?.cancel()
        jobVersion = GlobalScope.launch(Dispatchers.Main) {
            delay(3000)
            val pInfo: PackageInfo = TheApplication.application.applicationContext.packageManager.getPackageInfo(TheApplication.application.applicationContext.packageName, 0)
            val currentVersion = pInfo.versionName

            val version = try {
                thirdPartyService.githubVersion()
            } catch (e: Exception) {
                currentVersion
            }

            if (version != currentVersion) { // показать окно обновления
                Utils.showUpdateAlert(requireContext(), currentVersion, version)
            }
        }
        return view
    }

    fun updateData() {
        jobUpdate?.cancel()
        jobUpdate = GlobalScope.launch(Dispatchers.Main) {
            depositManager.refreshDeposit()
            depositManager.refreshKotleta()
            adapterList.setData(depositManager.portfolioPositions)
            updateTitle()
        }
    }

    private fun updateTitle() {
        val act = requireActivity() as AppCompatActivity

        val percent = depositManager.getPercentBusyInStocks()
        act.supportActionBar?.title = "Депозит $percent%"
    }

    inner class ItemPortfolioRecyclerViewAdapter(
        private var values: List<PortfolioPosition>
    ) : RecyclerView.Adapter<ItemPortfolioRecyclerViewAdapter.ViewHolder>() {

        fun setData(newValues: List<PortfolioPosition>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fragment_portfolio_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.position = item
            holder.tickerView.text = "${position + 1}) ${item.ticker}"

            val avg = item.getAveragePrice()
            holder.volumePiecesView.text = "${item.blocked.toInt()}/${item.lots} шт."
            holder.priceView.text = "${avg.toMoney(item.stock)}"

            val profit = item.getProfitAmount()
            holder.changePriceAbsoluteView.text = profit.toMoney(item.stock)

            var percent = item.getProfitPercent()

            // инвертировать доходность шорта
            percent *= sign(item.lots.toDouble())

            var totalCash = item.balance * avg
            totalCash += profit
            holder.volumeCashView.text = totalCash.toMoney(item.stock)

            val emoji = when {
                percent <= -10 -> " 🤬"
                percent <= -5 -> " 😡"
                percent <= -3 -> " 😱"
                percent <= -1 -> " 😰"
                percent >= 10 -> " 🤩"
                percent >= 5 -> " 😍"
                percent >= 3 -> " 🥳"
                percent >= 1 -> " 🤑"
                else -> ""
            }

            holder.changePricePercentView.text = percent.toPercent() + emoji

            holder.changePriceAbsoluteView.setTextColor(Utils.getColorForValue(percent))
            holder.changePricePercentView.setTextColor(Utils.getColorForValue(percent))

            holder.buttonOrderbook.setOnClickListener {
                holder.position.stock?.let {
                    orderbookManager.start(it)
                    view?.findNavController()?.navigate(R.id.action_nav_portfolio_to_nav_orderbook)
                }
            }

            holder.itemView.setOnClickListener {
                holder.position.stock?.let {
                    Utils.openTinkoffForTicker(requireContext(), it.instrument.ticker)
                }
            }

            item.stock?.let { stock ->
                holder.sectorView.text = stock.getSectorName()
                holder.sectorView.setTextColor(Utils.getColorForSector(stock.closePrices?.sector))

                if (stock.report != null) {
                    holder.reportView.text = stock.getReportInfo()
                    holder.reportView.visibility = View.VISIBLE
                } else {
                    holder.reportView.visibility = View.GONE
                }
                holder.reportView.setTextColor(Utils.RED)
            }
        }

        override fun getItemCount(): Int = values.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            lateinit var position: PortfolioPosition
            val tickerView: TextView = view.findViewById(R.id.stock_item_ticker)
            val priceView: TextView = view.findViewById(R.id.stock_item_price)

            val buttonOrderbook: Button = view.findViewById(R.id.button_orderbook)

            val volumeCashView: TextView = view.findViewById(R.id.stock_item_volume_cash)
            val volumePiecesView: TextView = view.findViewById(R.id.stock_item_volume_pieces)

            val changePriceAbsoluteView: TextView = view.findViewById(R.id.stock_item_price_change_absolute)
            val changePricePercentView: TextView = view.findViewById(R.id.stock_item_price_change_percent)

            val reportView: TextView = view.findViewById(R.id.stock_report_info)
            val sectorView: TextView = view.findViewById(R.id.stock_sector)
        }
    }
}