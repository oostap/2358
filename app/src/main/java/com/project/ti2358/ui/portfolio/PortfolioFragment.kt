package com.project.ti2358.ui.portfolio

import android.content.pm.PackageInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.TheApplication
import com.project.ti2358.data.common.BasePosition
import com.project.ti2358.data.common.BrokerType
import com.project.ti2358.data.manager.*
import com.project.ti2358.data.tinkoff.model.TinkoffPosition
import com.project.ti2358.data.daager.service.ThirdPartyService
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
    private val positionManager: PositionManager by inject()

    private val tinkoffPortfolioManager: TinkoffPortfolioManager by inject()
    private val alorPortfolioManager: AlorPortfolioManager by inject()
    private val brokerManager: BrokerManager by inject()

    private val strategySpeaker: StrategySpeaker by inject()
    private val strategyTA: StrategyTA by inject()
    private val strategyTelegram: StrategyTelegram by inject()

    private var fragmentPortfolioBinding: FragmentPortfolioBinding? = null

    var adapterList: ItemPortfolioRecyclerViewAdapter = ItemPortfolioRecyclerViewAdapter(emptyList())
    var jobUpdate: Job? = null
    var jobVersion: Job? = null

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

        with(binding) {
            list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapterList

            updateButton.setOnClickListener {
                updateData()

//                strategyTelegram.sendDepo()
//                strategyTA.processALL()
            }

            ordersButton.setOnClickListener {
                view.findNavController().navigate(R.id.action_nav_portfolio_to_nav_orders)
            }
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
            brokerManager.refreshDeposit()
            brokerManager.refreshKotleta()
            adapterList.setData(brokerManager.getPositionsAll())
            updateTitle()
        }
    }

    private fun updateTitle() {
        if (isAdded) {
            val act = requireActivity() as AppCompatActivity

            var text = ""

            if (SettingsManager.getBrokerTinkoff()) {
                text += " ТИ=${tinkoffPortfolioManager.getPercentBusyInStocks()}%"
            }
            if (SettingsManager.getBrokerAlor()) {
                text += " ALOR=${alorPortfolioManager.getPercentBusyInStocks()}%"
            }
            act.supportActionBar?.title = text
        }
    }

    inner class ItemPortfolioRecyclerViewAdapter(var values: List<BasePosition>) : RecyclerView.Adapter<ItemPortfolioRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<BasePosition>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FragmentPortfolioItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentPortfolioItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val portfolioPosition = values[index]
                val stock = portfolioPosition.getPositionStock()
                val broker = if (portfolioPosition is TinkoffPosition) BrokerType.TINKOFF else BrokerType.ALOR

                with(binding) {
                    tickerView.text = "${index + 1}) ${stock?.getTickerLove()}"

                    lotsBlockedView.text = "(${brokerManager.getBlockedForPosition(portfolioPosition, stock, broker)}🔒)"

                    val avg = portfolioPosition.getAveragePrice()
                    var priceNow = "0.0$"
                    stock?.let {
                        lotsView.text = "${portfolioPosition.getLots() * it.instrument.lot}"
                        priceNow = (it.getPriceNow() / it.instrument.lot).toMoney(it)
                    }

                    priceView.text = "${avg.toMoney(stock)} ➡ $priceNow"

                    val profit = portfolioPosition.getProfitAmount()
                    priceChangeAbsoluteView.text = profit.toMoney(stock)

                    var percent = portfolioPosition.getProfitPercent()

                    // инвертировать доходность шорта
                    percent *= sign(portfolioPosition.getLots().toDouble())

                    val totalCash = portfolioPosition.getLots() * avg + profit
                    cashView.text = totalCash.toMoney(stock)

                    val emoji = Utils.getEmojiForPercent(percent)
                    priceChangePercentView.text = percent.toPercent() + emoji

                    priceView.setTextColor(Utils.getColorForValue(percent))
                    priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(percent))
                    priceChangePercentView.setTextColor(Utils.getColorForValue(percent))
                    orderbookButton.setOnClickListener {
                        if (stock != null) {
                            Utils.openOrderbookForStock(orderbookButton.findNavController(), orderbookManager, stock)
                        } else {
                            Utils.showMessageAlert(requireContext(), "Бумага не загружена, нажмите обновить или напишите разрабу что за бумага такая")
                        }
                    }

                    itemView.setOnClickListener {
                        if (stock != null) {
                            if (portfolioPosition is TinkoffPosition) {
                                positionManager.start(portfolioPosition)
                                itemView.findNavController().navigate(R.id.action_nav_portfolio_to_nav_portfolio_position)
                            }
                        } else {
                            Utils.showMessageAlert(requireContext(), "Какая-то странная бумага, нет такой в каталоге у ТИ!")
                        }
                    }

                    sectorView.text = ""
                    reportInfoView.text = ""
                    stock?.let { stock ->
                        sectorView.text = stock.getSectorName()
                        sectorView.setTextColor(Utils.getColorForSector(stock.closePrices?.sector))

                        if (stock.report != null) {
                            reportInfoView.text = stock.getReportInfo()
                            reportInfoView.visibility = View.VISIBLE
                        } else {
                            reportInfoView.visibility = View.GONE
                        }
                        reportInfoView.setTextColor(Utils.RED)
                    }

                    if (SettingsManager.getBrokerAlor() && SettingsManager.getBrokerTinkoff()) {
                        itemView.setBackgroundColor(Utils.getColorForBrokerValue(broker, true))
                    } else {
                        itemView.setBackgroundColor(Utils.getColorForIndex(index))
                    }
                }
            }
        }
    }
}