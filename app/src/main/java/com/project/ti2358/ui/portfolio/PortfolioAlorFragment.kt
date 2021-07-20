package com.project.ti2358.ui.portfolio

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
import com.project.ti2358.data.alor.model.PositionAlor
import com.project.ti2358.data.manager.*
import com.project.ti2358.databinding.FragmentPortfolioAlorBinding
import com.project.ti2358.databinding.FragmentPortfolioAlorItemBinding
import com.project.ti2358.service.Utils
import com.project.ti2358.service.toMoney
import com.project.ti2358.service.toPercent
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import kotlin.math.sign

@KoinApiExtension
class PortfolioAlorFragment : Fragment(R.layout.fragment_portfolio_alor) {
    private val orderbookManager: OrderbookManager by inject()
    private val alorPortfolioManager: AlorPortfolioManager by inject()

    private var fragmentPortfolioAlorBinding: FragmentPortfolioAlorBinding? = null

    var adapterList: ItemPortfolioRecyclerViewAdapter = ItemPortfolioRecyclerViewAdapter(emptyList())
    var jobUpdate: Job? = null
    var jobVersion: Job? = null

    override fun onDestroy() {
        jobUpdate?.cancel()
        jobVersion?.cancel()
        fragmentPortfolioAlorBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentPortfolioAlorBinding.bind(view)
        fragmentPortfolioAlorBinding = binding

        with(binding) {
            list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapterList

            updateButton.setOnClickListener {
                updateData()
            }

            ordersButton.setOnClickListener {
                view.findNavController().navigate(R.id.action_nav_portfolio_alor_to_nav_orders_alor)
            }
        }

        updateData()
    }

    fun updateData() {
        jobUpdate?.cancel()
        jobUpdate = GlobalScope.launch(Dispatchers.Main) {
            alorPortfolioManager.refreshDeposit()
            alorPortfolioManager.refreshKotleta()
            adapterList.setData(alorPortfolioManager.getPositions())
            updateTitle()
        }
    }

    private fun updateTitle() {
        if (isAdded) {
            val act = requireActivity() as AppCompatActivity
            val percent = alorPortfolioManager.getPercentBusyInStocks()
            act.supportActionBar?.title = "Депозит ALOR $percent%"
        }
    }

    inner class ItemPortfolioRecyclerViewAdapter(var values: List<PositionAlor>) : RecyclerView.Adapter<ItemPortfolioRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<PositionAlor>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FragmentPortfolioAlorItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentPortfolioAlorItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val portfolioPosition = values[index]
                with(binding) {
                    tickerView.text = "${index + 1}) ${portfolioPosition.stock?.getTickerLove()}"

                    lotsBlockedView.text = if (portfolioPosition.getBlocked().toInt() > 0) "(${portfolioPosition.getBlocked().toInt()}🔒)" else ""

                    val avg = portfolioPosition.avgPrice
                    var priceNow = "0.0$"
                    portfolioPosition.stock?.let {
                        lotsView.text = "${portfolioPosition.getLots() * it.instrument.lot}"
                        priceNow = (it.getPriceNow() / it.instrument.lot).toMoney(it)
                    }

                    priceView.text = "${avg.toMoney(portfolioPosition.stock)} ➡ $priceNow"

                    val profit = portfolioPosition.getProfitAmount()
                    priceChangeAbsoluteView.text = profit.toMoney(portfolioPosition.stock)

                    var percent = portfolioPosition.getProfitPercent()

                    // инвертировать доходность шорта
                    percent *= sign(portfolioPosition.getLots().toDouble())

                    val totalCash = portfolioPosition.getLots() * avg + profit
                    cashView.text = totalCash.toMoney(portfolioPosition.stock)

                    val emoji = Utils.getEmojiForPercent(percent)
                    priceChangePercentView.text = percent.toPercent() + emoji

                    priceView.setTextColor(Utils.getColorForValue(percent))
                    priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(percent))
                    priceChangePercentView.setTextColor(Utils.getColorForValue(percent))
                    orderbookButton.setOnClickListener {
                        if (portfolioPosition.stock == null) {
                            Utils.showMessageAlert(requireContext(), "Какая-то странная бумага, нет такой в каталоге у ТИ!")
                        } else {
                            portfolioPosition.stock?.let {
                                Utils.openOrderbookForStock(orderbookButton.findNavController(), orderbookManager, it)
                            }
                        }
                    }

                    itemView.setOnClickListener {
//                        if (portfolioPosition.stock == null) {
//                            Utils.showMessageAlert(requireContext(), "Какая-то странная бумага, нет такой в каталоге у ТИ!")
//                        } else {
//                            positionManager.start(portfolioPosition)
//                            itemView.findNavController().navigate(R.id.action_nav_portfolio_to_nav_portfolio_position)
//                        }
                    }

                    sectorView.text = ""
                    reportInfoView.text = ""
                    portfolioPosition.stock?.let { stock ->
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
                    itemView.setBackgroundColor(Utils.getColorForIndex(index))
                }
            }
        }
    }
}