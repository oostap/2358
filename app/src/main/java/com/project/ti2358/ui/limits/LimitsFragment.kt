package com.project.ti2358.ui.limits

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.*
import com.project.ti2358.databinding.FragmentLimitsAllItemBinding
import com.project.ti2358.databinding.FragmentLimitsBinding
import com.project.ti2358.databinding.FragmentLimitsItemBinding
import com.project.ti2358.service.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.util.*

@KoinApiExtension
class LimitsFragment : Fragment(R.layout.fragment_limits) {
    val orderbookManager: OrderbookManager by inject()
    val chartManager: ChartManager by inject()
    val strategyLimits: StrategyLimits by inject()

    private var fragmentLimitsBinding: FragmentLimitsBinding? = null

    var adapterListAll = ItemLimitsAllRecyclerViewAdapter(emptyList())
    var adapterListLimits = ItemLimitsRecyclerViewAdapter(emptyList())
    lateinit var stocks: MutableList<Stock>

    val colorDefault = Utils.DARK_BLUE
    val colorSelect = Utils.RED

    var status: Int = 0

    override fun onDestroy() {
        fragmentLimitsBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentLimitsBinding.bind(view)
        fragmentLimitsBinding = binding

        with(binding) {
            listAll.addItemDecoration(DividerItemDecoration(listAll.context, DividerItemDecoration.VERTICAL))
            listAll.layoutManager = LinearLayoutManager(context)
            listAll.adapter = adapterListAll

            startButton.setOnClickListener {
                if (Utils.isServiceRunning(requireContext(), StrategyLimitsService::class.java)) {
                    requireContext().stopService(Intent(context, StrategyLimitsService::class.java))
                } else {
                    Utils.startService(requireContext(), StrategyLimitsService::class.java)
                }
                updateServiceButtonText()
            }
            updateServiceButtonText()

            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    processText(query)
                    return false
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    processText(newText)
                    return false
                }

                fun processText(text: String) {
                    updateData(text)
                }
            })

            searchView.setOnCloseListener {
                updateData(searchView.query.toString())
                false
            }

            allButton.setOnClickListener {
                allButton.setBackgroundColor(colorDefault)
                upButton.setBackgroundColor(colorDefault)
                downButton.setBackgroundColor(colorDefault)
                allButton.setBackgroundColor(colorSelect)

                updateData()
                status = 0
            }

            upButton.setOnClickListener {
                allButton.setBackgroundColor(colorDefault)
                upButton.setBackgroundColor(colorDefault)
                downButton.setBackgroundColor(colorDefault)

                listAll.visibility = View.GONE
                listLimits.visibility = View.VISIBLE
                status = 1
                adapterListLimits.setData(strategyLimits.upStockLimits)
                upButton.setBackgroundColor(colorSelect)
                updateTitle()
            }

            downButton.setOnClickListener {
                allButton.setBackgroundColor(colorDefault)
                upButton.setBackgroundColor(colorDefault)
                downButton.setBackgroundColor(colorDefault)

                listAll.visibility = View.GONE
                listLimits.visibility = View.VISIBLE
                status = 2
                adapterListLimits.setData(strategyLimits.downStockLimits)
                downButton.setBackgroundColor(colorSelect)
                updateTitle()
            }

            listAll.visibility = View.VISIBLE
            listLimits.visibility = View.GONE
            adapterListAll.setData(strategyLimits.stocks)
        }
    }

    private fun updateData(search: String = "") {
        GlobalScope.launch(Dispatchers.Main) {
            fragmentLimitsBinding?.apply {
                listAll.visibility = View.VISIBLE
                listLimits.visibility = View.GONE
            }

            stocks = strategyLimits.process()
            stocks = strategyLimits.resort()
            if (search != "") {
                stocks = Utils.search(stocks, search)
            }
            adapterListAll.setData(stocks)

            updateTitle()
        }
    }

    private fun updateServiceButtonText() {
        if (Utils.isServiceRunning(requireContext(), StrategyLimitsService::class.java)) {
            fragmentLimitsBinding?.startButton?.text = getString(R.string.stop)
        } else {
            fragmentLimitsBinding?.startButton?.text = getString(R.string.start)
        }
    }

    private fun updateTitle() {
        if (isAdded) {
            val act = requireActivity() as AppCompatActivity
            act.supportActionBar?.title = "Лимиты - " + if (status == 0) "Все (${stocks.size} шт.)" else if (status == 1) "Верхние" else "Нижние"

        }
    }

    inner class ItemLimitsAllRecyclerViewAdapter(private var values: List<Stock>) : RecyclerView.Adapter<ItemLimitsAllRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<Stock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FragmentLimitsAllItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentLimitsAllItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val stock = values[index]
                with(binding) {
                    tickerView.text = "${index + 1}) ${stock.getTickerLove()}"

                    upLimitView.text = "⬆️%.2f$".format(stock.stockInfo?.limit_up)
                    downLimitView.text = "⬇️%.2f$".format(stock.stockInfo?.limit_down)

                    priceView.text = "${stock.getPrice2300().toMoney(stock)} ➡ ${stock.getPriceRaw().toMoney(stock)}"

                    val upLimitChange = Utils.getPercentFromTo(stock.stockInfo?.limit_up ?: 0.0, stock.getPriceRaw())
                    val downLimitChange = Utils.getPercentFromTo(stock.stockInfo?.limit_down ?: 0.0, stock.getPriceRaw())

                    upLimitPercentView.text = upLimitChange.toPercent()
                    downLimitPercentView.text = downLimitChange.toPercent()

                    upLimitPercentView.setTextColor(Utils.getColorForValue(upLimitChange))
                    downLimitPercentView.setTextColor(Utils.getColorForValue(downLimitChange))

                    itemView.setOnClickListener {
                        Utils.openOrderbookForStock(it.findNavController(), orderbookManager, stock)
                    }

                    chartButton.setOnClickListener {
                        Utils.openChartForStock(it.findNavController(), chartManager, stock)
                    }

                    sectorView.text = stock.getSectorName()
                    sectorView.setTextColor(Utils.getColorForSector(stock.closePrices?.sector))

                    if (stock.report != null) {
                        reportInfoView.text = stock.getReportInfo()
                        reportInfoView.visibility = View.VISIBLE
                    } else {
                        reportInfoView.visibility = View.GONE
                    }
                    reportInfoView.setTextColor(Utils.RED)
                    itemView.setBackgroundColor(Utils.getColorForIndex(index))
                }
            }
        }
    }

    inner class ItemLimitsRecyclerViewAdapter(private var values: List<StockLimit>) : RecyclerView.Adapter<ItemLimitsRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<StockLimit>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FragmentLimitsItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentLimitsItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val limitStock = values[index]
                with(binding) {
                    tickerView.text = "${index + 1}) ${limitStock.stock.getTickerLove()}"

                    val deltaMinutes = ((Calendar.getInstance().time.time - limitStock.fireTime) / 60.0 / 1000.0).toInt()
                    minutesView.text = "$deltaMinutes мин"

                    priceView.text = "${limitStock.stock.getPrice2300().toMoney(limitStock.stock)} ➡ ${limitStock.priceFire.toMoney(limitStock.stock)}"

                    if (limitStock.type in listOf(LimitType.NEAR_UP, LimitType.ABOVE_UP, LimitType.ON_UP)) {
                        val upLimitChange = Utils.getPercentFromTo(limitStock.stock.stockInfo?.limit_up ?: 0.0, limitStock.priceFire)
                        leftChangePercentView.text = upLimitChange.toPercent()
                        leftChangeAbsoluteView.text = (limitStock.stock.stockInfo?.limit_up ?: 0.0 - limitStock.priceFire).toMoney(limitStock.stock)
                        leftChangeAbsoluteView.setTextColor(Utils.getColorForValue(upLimitChange))

                        limitView.text = "⬆️%.2f$".format(limitStock.stock.stockInfo?.limit_up ?: 0.0)
                    } else {
                        val downLimitChange = Utils.getPercentFromTo(limitStock.stock.stockInfo?.limit_down ?: 0.0, limitStock.priceFire)
                        leftChangePercentView.text = downLimitChange.toPercent()
                        leftChangeAbsoluteView.text = (limitStock.stock.stockInfo?.limit_up ?: 0.0 - limitStock.priceFire).toMoney(limitStock.stock)
                        leftChangeAbsoluteView.setTextColor(Utils.getColorForValue(downLimitChange))

                        limitView.text = "⬇️️%.2f$".format(limitStock.stock.stockInfo?.limit_down ?: 0.0)
                    }

                    itemView.setOnClickListener {
                        Utils.openOrderbookForStock(it.findNavController(), orderbookManager, limitStock.stock)
                    }

                    chartButton.setOnClickListener {
                        Utils.openChartForStock(it.findNavController(), chartManager, limitStock.stock)
                    }

                    sectorView.text = limitStock.stock.getSectorName()
                    sectorView.setTextColor(Utils.getColorForSector(limitStock.stock.closePrices?.sector))

                    if (limitStock.stock.report != null) {
                        reportInfoView.text = limitStock.stock.getReportInfo()
                        reportInfoView.visibility = View.VISIBLE
                    } else {
                        reportInfoView.visibility = View.GONE
                    }
                    reportInfoView.setTextColor(Utils.RED)
                    itemView.setBackgroundColor(Utils.getColorForIndex(index))
                }
            }
        }
    }
}