package com.project.ti2358.ui.portfolio

import android.content.pm.PackageInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.TheApplication
import com.project.ti2358.data.manager.*
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

    companion object {
        var versionUpdateShowed: Boolean = false
    }
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

        val buttonUpdate = view.findViewById<Button>(R.id.button_update)
        buttonUpdate.setOnClickListener {
            updateData()
        }

        val buttonOrder = view.findViewById<Button>(R.id.buttonOrders)
        buttonOrder.setOnClickListener {
            view.findNavController().navigate(R.id.action_nav_portfolio_to_nav_orders)
        }

        updateData()

        if (!versionUpdateShowed) {
            jobVersion?.cancel()
            jobVersion = GlobalScope.launch(Dispatchers.Main) {
                delay(3000)
                val pInfo: PackageInfo = TheApplication.application.applicationContext.packageManager.getPackageInfo(
                    TheApplication.application.applicationContext.packageName,
                    0
                )
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
            versionUpdateShowed = true
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

            if (item.blocked.toInt() > 0) {
                holder.lotsBlockedView.text = "(${item.blocked.toInt()}🔒)"
            } else {
                holder.lotsBlockedView.text = ""
            }

            holder.lotsView.text = "${item.lots}"

            val avg = item.getAveragePrice()
            holder.priceView.text = "${avg.toMoney(item.stock)} ➡ ${item.stock?.getPriceString()}"

            val profit = item.getProfitAmount()
            holder.changePriceAbsoluteView.text = profit.toMoney(item.stock)

            var percent = item.getProfitPercent()

            // инвертировать доходность шорта
            percent *= sign(item.lots.toDouble())

            var totalCash = item.balance * avg
            totalCash += profit
            holder.cashView.text = totalCash.toMoney(item.stock)

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

            holder.priceView.setTextColor(Utils.getColorForValue(percent))
            holder.changePriceAbsoluteView.setTextColor(Utils.getColorForValue(percent))
            holder.changePricePercentView.setTextColor(Utils.getColorForValue(percent))

            holder.orderbookImage.setOnClickListener {
                // TODO: тест трейлинг стопа для позы
//                holder.position.stock?.let {
//                    var purchase = PurchaseStock(it)
//                    purchase.position = holder.position
//                    purchase.trailingStop = true
//                    purchase.trailingStopTakeProfitPercentActivation = SettingsManager.getTrailingStopTakeProfitPercentActivation()
//                    purchase.trailingStopTakeProfitPercentDelta = SettingsManager.getTrailingStopTakeProfitPercentDelta()
//                    purchase.trailingStopStopLossPercent = SettingsManager.getTrailingStopStopLossPercent()
//                    purchase.processInitialProfit()
//                    purchase.sellWithTrailing()

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

            holder.itemView.setBackgroundColor(Utils.getColorForIndex(position))
        }

        override fun getItemCount(): Int = values.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            lateinit var position: PortfolioPosition
            val tickerView: TextView = view.findViewById(R.id.stock_item_ticker)
            val priceView: TextView = view.findViewById(R.id.stock_item_price)

            val orderbookImage: ImageView = view.findViewById(R.id.orderbook)

            val cashView: TextView = view.findViewById(R.id.stock_item_cash)
            val lotsView: TextView = view.findViewById(R.id.stock_item_lots)
            val lotsBlockedView: TextView = view.findViewById(R.id.stock_item_lots_blocked)

            val changePriceAbsoluteView: TextView = view.findViewById(R.id.stock_item_price_change_absolute)
            val changePricePercentView: TextView = view.findViewById(R.id.stock_item_price_change_percent)

            val reportView: TextView = view.findViewById(R.id.stock_report_info)
            val sectorView: TextView = view.findViewById(R.id.stock_sector)
        }
    }
}