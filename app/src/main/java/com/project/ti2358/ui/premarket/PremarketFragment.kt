package com.project.ti2358.ui.premarket

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
import com.project.ti2358.databinding.FragmentPremarketBinding
import com.project.ti2358.databinding.FragmentPremarketItemBinding
import com.project.ti2358.service.ScreenerType
import com.project.ti2358.service.Utils
import com.project.ti2358.service.toMoney
import com.project.ti2358.service.toPercent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.util.*

@KoinApiExtension
class PremarketFragment : Fragment(R.layout.fragment_premarket) {
    val stockManager: StockManager by inject()
    val strategyPremarket: StrategyPremarket by inject()
    val orderbookManager: OrderbookManager by inject()
    val chartManager: ChartManager by inject()

    private var fragmentPremarketBinding: FragmentPremarketBinding? = null

    var adapterList: ItemPremarketRecyclerViewAdapter = ItemPremarketRecyclerViewAdapter(emptyList())
    lateinit var stocks: MutableList<Stock>
    var job: Job? = null

    override fun onDestroy() {
        job?.cancel()
        fragmentPremarketBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentPremarketBinding.bind(view)
        fragmentPremarketBinding = binding

        with(binding) {
            list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapterList

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

            from2300Button.setOnClickListener {
                strategyPremarket.screenerTypeFrom = ScreenerType.screener2300
                updateData(searchView.query.toString())
            }

            from0145Button.setOnClickListener {
                strategyPremarket.screenerTypeFrom = ScreenerType.screener0145
                updateData(searchView.query.toString())
            }

            from0300Button.setOnClickListener {
                strategyPremarket.screenerTypeFrom = ScreenerType.screener0300
                updateData(searchView.query.toString())
            }

            from0700Button.setOnClickListener {
                strategyPremarket.screenerTypeFrom = ScreenerType.screener0700
                updateData(searchView.query.toString())
            }
            ////////?////////?////////?////////?////////?////////?////////?////////?
            fromNowButton.setOnClickListener {
                strategyPremarket.screenerTypeFrom = ScreenerType.screenerNow
                updateData(searchView.query.toString())
            }

            to2300Button.setOnClickListener {
                strategyPremarket.screenerTypeTo = ScreenerType.screener2300
                updateData(searchView.query.toString())
            }

            to0145Button.setOnClickListener {
                strategyPremarket.screenerTypeTo = ScreenerType.screener0145
                updateData(searchView.query.toString())
            }

            to0300Button.setOnClickListener {
                strategyPremarket.screenerTypeTo = ScreenerType.screener0300
                updateData(searchView.query.toString())
            }

            to0700Button.setOnClickListener {
                strategyPremarket.screenerTypeTo = ScreenerType.screener0700
                updateData(searchView.query.toString())
            }

            toNowButton.setOnClickListener {
                strategyPremarket.screenerTypeTo = ScreenerType.screenerNow
                updateData(searchView.query.toString())
            }
        }

        job?.cancel()
        job = GlobalScope.launch(Dispatchers.Main) {
            stockManager.reloadClosePrices()
            updateData()
        }

        updateData()
    }

    private fun updateData(search: String = "") {
        stocks = strategyPremarket.process()
        stocks = strategyPremarket.resort()
        if (search != "") {
            stocks = Utils.search(stocks, search)
        }
        adapterList.setData(stocks)
        updateButtons()
        updateTitle()
    }

    private fun updateTitle() {
        if (isAdded) {
            val act = requireActivity() as AppCompatActivity
            act.supportActionBar?.title = "Премаркет ${stocks.size}"
        }
    }

    private fun updateButtons() {
        fragmentPremarketBinding?.apply {
            val colorDefault = Utils.DARK_BLUE
            val colorSelect = Utils.RED

            from2300Button.setBackgroundColor(colorDefault)
            from0145Button.setBackgroundColor(colorDefault)
            from0300Button.setBackgroundColor(colorDefault)
            from0700Button.setBackgroundColor(colorDefault)
            fromNowButton.setBackgroundColor(colorDefault)

            to2300Button.setBackgroundColor(colorDefault)
            to0145Button.setBackgroundColor(colorDefault)
            to0300Button.setBackgroundColor(colorDefault)
            to0700Button.setBackgroundColor(colorDefault)
            toNowButton.setBackgroundColor(colorDefault)

            when (strategyPremarket.screenerTypeFrom) {
                ScreenerType.screener2300 -> from2300Button.setBackgroundColor(colorSelect)
                ScreenerType.screener0145 -> from0145Button.setBackgroundColor(colorSelect)
                ScreenerType.screener0300 -> from0300Button.setBackgroundColor(colorSelect)
                ScreenerType.screener0700 -> from0700Button.setBackgroundColor(colorSelect)
                ScreenerType.screenerNow -> fromNowButton.setBackgroundColor(colorSelect)
            }

            when (strategyPremarket.screenerTypeTo) {
                ScreenerType.screener2300 -> to2300Button.setBackgroundColor(colorSelect)
                ScreenerType.screener0145 -> to0145Button.setBackgroundColor(colorSelect)
                ScreenerType.screener0300 -> to0300Button.setBackgroundColor(colorSelect)
                ScreenerType.screener0700 -> to0700Button.setBackgroundColor(colorSelect)
                ScreenerType.screenerNow -> toNowButton.setBackgroundColor(colorSelect)
            }
        }
    }

    inner class ItemPremarketRecyclerViewAdapter(private var values: List<Stock>) : RecyclerView.Adapter<ItemPremarketRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<Stock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FragmentPremarketItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentPremarketItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val stock = values[index]
                with(binding) {
                    tickerView.text = "${index + 1}) ${stock.getTickerLove()}"

                    val volume = stock.getTodayVolume() / 1000f
                    volumeSharesView.text = "%.1fk".format(locale = Locale.US, volume)

                    val volumeCash = stock.dayVolumeCash / 1000f / 1000f
                    volumeCashView.text = "%.2fM$".format(locale = Locale.US, volumeCash)

                    priceView.text = "${stock.priceScreenerFrom.toMoney(stock)} ➡ ${stock.priceScreenerTo.toMoney(stock)}"

                    priceChangeAbsoluteView.text = stock.changePriceScreenerAbsolute.toMoney(stock)
                    priceChangePercentView.text = stock.changePriceScreenerPercent.toPercent()

                    priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(stock.changePriceScreenerPercent))
                    priceChangePercentView.setTextColor(Utils.getColorForValue(stock.changePriceScreenerPercent))
                    priceView.setTextColor(Utils.getColorForValue(stock.changePriceScreenerPercent))

                    itemView.setOnClickListener {
                        view?.findNavController()?.let {
                            Utils.openOrderbookForStock(it, orderbookManager, stock)
                        }
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
}