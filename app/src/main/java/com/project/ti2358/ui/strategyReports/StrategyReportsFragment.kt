package com.project.ti2358.ui.strategyReports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.manager.StockManager
import com.project.ti2358.data.manager.StrategyReports
import com.project.ti2358.databinding.FragmentReportsBinding
import com.project.ti2358.databinding.FragmentReportsItemBinding
import com.project.ti2358.service.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class ReportsFragment : Fragment(R.layout.fragment_reports) {
    private val stockManager: StockManager by inject()
    private val strategyReports: StrategyReports by inject()

    private var fragmentReportsBinding: FragmentReportsBinding? = null

    var adapterList: ItemReportsRecyclerViewAdapter = ItemReportsRecyclerViewAdapter(emptyList())
    lateinit var stocks: MutableList<Stock>
    var job : Job? = null

    override fun onDestroy() {
        job?.cancel()
        fragmentReportsBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentReportsBinding.bind(view)
        fragmentReportsBinding = binding

        binding.list.addItemDecoration(DividerItemDecoration(binding.list.context, DividerItemDecoration.VERTICAL))
        binding.list.layoutManager = LinearLayoutManager(context)
        binding.list.adapter = adapterList

        binding.reportButton.setOnClickListener {
            updateDataReport()
        }

        binding.divButton.setOnClickListener {
            updateDataDivs()
        }

        updateDataReport()

        job?.cancel()
        job = GlobalScope.launch(Dispatchers.Main) {
            stockManager.reloadReports()
            updateDataReport()
        }
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

    inner class ItemReportsRecyclerViewAdapter(private var values: List<Stock>) : RecyclerView.Adapter<ItemReportsRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<Stock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FragmentReportsItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentReportsItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val stock = values[index]

                with(binding) {
                    tickerView.text = "${index + 1}) ${stock.getTickerLove()}"
                    priceView.text = "${stock.getPrice2359String()} ➡ ${stock.getPriceString()}"

                    priceChangeAbsoluteView.text = stock.changePrice2359DayAbsolute.toMoney(stock)
                    priceChangePercentView.text = stock.changePrice2359DayPercent.toPercent()

                    priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(stock.changePrice2359DayAbsolute))
                    priceChangePercentView.setTextColor(Utils.getColorForValue(stock.changePrice2359DayAbsolute))

                    itemView.setOnClickListener {
                        Utils.openTinkoffForTicker(requireContext(), stock.ticker)
                    }

                    sectorView.text = stock.getSectorName()
                    sectorView.setTextColor(Utils.getColorForSector(stock.closePrices?.sector))

                    stock.dividend?.let {
                        val emoji = if (it.profit > 1.0) " 🤑" else ""
                        revView.text = "+${it.profit}%$emoji"
                        revView.setTextColor(Utils.GREEN)
                        epsView.visibility = View.GONE

                        dateView.text = it.date_format
                        dateView.setTextColor(Utils.RED)
                    }

                    stock.report?.let {
                        if (it.estimate_rev_per != null) {
                            if (it.estimate_rev_per > 0) {
                                revView.text = "REV: +${it.estimate_rev_per}$"
                            } else {
                                revView.text = "REV: ${it.estimate_rev_per}$"
                            }
                            revView.setTextColor(Utils.getColorForValue(it.estimate_rev_per))
                        }

                        if (it.estimate_eps != null) {
                            if (it.estimate_eps > 0) {
                                epsView.text = "EPS: (+${it.estimate_eps})%"
                            } else {
                                epsView.text = "EPS: (${it.estimate_eps})%"
                            }
                            epsView.setTextColor(Utils.getColorForValue(it.estimate_eps))
                        }

                        if (it.actual_rev_per != null) {
                            if (it.actual_rev_per > 0) {
                                revView.text = "REV: +${it.actual_rev_per}$"
                            } else {
                                revView.text = "REV: ${it.actual_rev_per}$"
                            }
                            revView.setTextColor(Utils.getColorForValue(it.actual_rev_per))
                        }

                        if (it.actual_eps != null) {
                            if (it.actual_eps > 0) {
                                epsView.text = "EPS: (+${it.actual_eps})%"
                            } else {
                                epsView.text = "EPS: (${it.actual_eps})%"
                            }
                            epsView.setTextColor(Utils.getColorForValue(it.actual_eps))
                        }

                        var tod = if (it.tod == "post") " 🌚" else " 🌞"
                        if (it.actual_eps != null || it.actual_rev_per != null) {
                            tod += "✅"
                        }
                        dateView.text = "${it.date_format} $tod"
                        dateView.setTextColor(Utils.RED)
                    }

                    itemView.setBackgroundColor(Utils.getColorForIndex(index))
                }
            }
        }
    }
}