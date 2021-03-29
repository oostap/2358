package com.project.ti2358.ui.portfolio

import android.content.pm.PackageInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.project.ti2358.databinding.FragmentPortfolioBinding
import com.project.ti2358.databinding.FragmentPortfolioItemBinding
import com.project.ti2358.service.Utils
import com.project.ti2358.service.toMoney
import com.project.ti2358.service.toPercent
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.lang.Exception
import kotlin.math.sign

@KoinApiExtension
class PortfolioFragment : Fragment(R.layout.fragment_portfolio) {
    private val orderbookManager: OrderbookManager by inject()
    private val thirdPartyService: ThirdPartyService by inject()
    val depositManager: DepositManager by inject()
    var adapterList: ItemPortfolioRecyclerViewAdapter = ItemPortfolioRecyclerViewAdapter(emptyList())

    var jobUpdate: Job? = null
    var jobVersion: Job? = null

    private var fragmentPortfolioBinding: FragmentPortfolioBinding? = null

    companion object {
        var versionUpdateShowed: Boolean = false
    }

    override fun onDestroy() {
        jobUpdate?.cancel()
        jobVersion?.cancel()
        fragmentPortfolioBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentPortfolioBinding.bind(view)
        fragmentPortfolioBinding = binding

        binding.list.addItemDecoration(DividerItemDecoration(binding.list.context, DividerItemDecoration.VERTICAL))
        with(binding.list) {
            layoutManager = LinearLayoutManager(context)
            adapter = adapterList
        }

        binding.buttonUpdate.setOnClickListener {
            updateData()
        }

        binding.buttonOrders.setOnClickListener {
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
        if (isAdded) {
            val act = requireActivity() as AppCompatActivity
            val percent = depositManager.getPercentBusyInStocks()
            act.supportActionBar?.title = "Депозит $percent%"
        }
    }

    inner class ItemPortfolioRecyclerViewAdapter(var values: List<PortfolioPosition>) : RecyclerView.Adapter<ItemPortfolioRecyclerViewAdapter.ViewHolder>() {

        fun setData(newValues: List<PortfolioPosition>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(FragmentPortfolioItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)

        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentPortfolioItemBinding) : RecyclerView.ViewHolder(binding.root) {
            lateinit var portfolioPosition: PortfolioPosition

            fun bind(index: Int) {
                val item = values[index]
                portfolioPosition = item
                binding.tickerView.text = "${index + 1}) ${portfolioPosition.stock?.getTickerLove()}"

                if (item.blocked.toInt() > 0) {
                    binding.lotsBlockedView.text = "(${item.blocked.toInt()}🔒)"
                } else {
                    binding.lotsBlockedView.text = ""
                }

                binding.lotsView.text = "${item.lots}"

                val avg = item.getAveragePrice()
                binding.priceView.text = "${avg.toMoney(item.stock)} ➡ ${item.stock?.getPriceString()}"

                val profit = item.getProfitAmount()
                binding.priceChangeAbsoluteView.text = profit.toMoney(item.stock)

                var percent = item.getProfitPercent()

                // инвертировать доходность шорта
                percent *= sign(item.lots.toDouble())

                var totalCash = item.balance * avg
                totalCash += profit
                binding.totalCashView.text = totalCash.toMoney(item.stock)

                val emoji = when {
                    percent <= -20 -> " 💩"
                    percent <= -15 -> " 🦌"
                    percent <= -10 -> " 🤬"
                    percent <= -5 -> " 😡"
                    percent <= -3 -> " 😱"
                    percent <= -1 -> " 😰"
                    percent >= 20 -> " 🤪️"
                    percent >= 15 -> " ❤️"
                    percent >= 10 -> " 🤩"
                    percent >= 5 -> " 😍"
                    percent >= 3 -> " 🥳"
                    percent >= 1 -> " 🤑"
                    else -> ""
                }

                binding.priceChangePercentView.text = percent.toPercent() + emoji

                binding.priceView.setTextColor(Utils.getColorForValue(percent))
                binding.priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(percent))
                binding.priceChangePercentView.setTextColor(Utils.getColorForValue(percent))

                binding.orderbookButton.setOnClickListener {
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

                    portfolioPosition.stock?.let {
                        orderbookManager.start(it)
                        view?.findNavController()?.navigate(R.id.action_nav_portfolio_to_nav_orderbook)
                    }
                }

                itemView.setOnClickListener {
                    portfolioPosition.stock?.let {
                        Utils.openTinkoffForTicker(requireContext(), it.ticker)
                    }
                }

                item.stock?.let { stock ->
                    binding.sectorView.text = stock.getSectorName()
                    binding.sectorView.setTextColor(Utils.getColorForSector(stock.closePrices?.sector))

                    if (stock.report != null) {
                        binding.reportInfoView.text = stock.getReportInfo()
                        binding.reportInfoView.visibility = View.VISIBLE
                    } else {
                        binding.reportInfoView.visibility = View.GONE
                    }
                    binding.reportInfoView.setTextColor(Utils.RED)
                }
                itemView.setBackgroundColor(Utils.getColorForIndex(index))
            }
        }
    }
}