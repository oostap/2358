package com.project.ti2358.ui.arbitration

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.*
import com.project.ti2358.databinding.FragmentArbitrationBinding
import com.project.ti2358.databinding.FragmentArbitrationItemBinding
import com.project.ti2358.service.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class ArbitrationFragment : Fragment(R.layout.fragment_arbitration) {
    val orderbookManager: OrderbookManager by inject()
    val chartManager: ChartManager by inject()
    val strategyArbitration: StrategyArbitration by inject()

    private var fragmentArbitrationBinding: FragmentArbitrationBinding? = null

    var adapterList: ItemStockAllRecyclerViewAdapter = ItemStockAllRecyclerViewAdapter(emptyList())
    var long: Boolean = true

    var stocksArbs: List<Stock> = mutableListOf()

    override fun onDestroy() {
        fragmentArbitrationBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentArbitrationBinding.bind(view)
        fragmentArbitrationBinding = binding

        with(binding) {
            listArbs.addItemDecoration(DividerItemDecoration(listArbs.context, DividerItemDecoration.VERTICAL))
            listArbs.layoutManager = LinearLayoutManager(context)
            listArbs.adapter = adapterList

            startButton.setOnClickListener {
                if (Utils.isServiceRunning(requireContext(), StrategyArbitrationService::class.java)) {
                    requireContext().stopService(Intent(context, StrategyArbitrationService::class.java))
                } else {
                    Utils.startService(requireContext(), StrategyArbitrationService::class.java)
                }
                updateServiceButtonText()
            }

            longButton.setOnClickListener {
                updateDataArb(true)
            }

            shortButton.setOnClickListener {
                updateDataArb(false)
            }
        }

        updateDataArb(true)
        updateServiceButtonText()
    }

    private fun updateDataArb(isLong: Boolean) {
        long = isLong

        GlobalScope.launch(Dispatchers.Main) {
            fragmentArbitrationBinding?.apply {
                listAll.visibility = View.GONE
                listArbs.visibility = View.VISIBLE

                val colorDefault = Utils.DARK_BLUE
                val colorSelect = Utils.RED

                longButton.setBackgroundColor(colorDefault)
                shortButton.setBackgroundColor(colorDefault)

                stocksArbs = strategyArbitration.resort(long)
                
                if (long) {
                    longButton.setBackgroundColor(colorSelect)
                } else {
                    shortButton.setBackgroundColor(colorSelect)
                }

                adapterList.setData(stocksArbs)
                updateTitle()
            }
        }
    }

    private fun updateTitle() {
        if (isAdded) {
            val act = requireActivity() as AppCompatActivity
            act.supportActionBar?.title = "Арбитраж - " + if (long) "LONG" else "SHORT"
        }
    }

    private fun updateServiceButtonText() {
        if (Utils.isServiceRunning(requireContext(), StrategyArbitrationService::class.java)) {
            fragmentArbitrationBinding?.startButton?.text = getString(R.string.stop)
        } else {
            fragmentArbitrationBinding?.startButton?.text = getString(R.string.start)
        }
    }

    inner class ItemStockAllRecyclerViewAdapter(private var values: List<Stock>) : RecyclerView.Adapter<ItemStockAllRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<Stock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FragmentArbitrationItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentArbitrationItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val stock = values[index]
                with(binding) {
                    tickerView.text = "${index + 1}) ${stock.getTickerLove()}"

//                    val deltaMinutes = ((Calendar.getInstance().time.time - arbStock.fireTime) / 60.0 / 1000.0).toInt()
                    minutesView.text = ""

                    val volume = stock.getTodayVolume() / 1000f
                    volumeSharesView.text = "%.1fk".format(volume)

                    if (long) {
                        val sum = stock.askPriceRU * stock.askLotsRU
                        sumView.text = "${sum.toMoney(stock)}"
                        priceView.text = "${stock.askPriceRU.toMoney(stock)} ➡ ${stock.getPrice2300().toMoney(stock)}"

                        priceChangeAbsoluteView.text = stock.changePriceArbLongAbsolute.toMoney(stock)
                        priceChangePercentView.text = stock.changePriceArbLongPercent.toPercent()

                        priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(stock.changePriceArbLongPercent))
                        priceChangePercentView.setTextColor(Utils.getColorForValue(stock.changePriceArbLongPercent))
                        priceView.setTextColor(Utils.getColorForValue(stock.changePriceArbLongPercent))
                    } else {
                        val sum = stock.bidPriceRU * stock.bidLotsRU
                        sumView.text = "${sum.toMoney(stock)}"

                        priceView.text = "${stock.bidPriceRU.toMoney(stock)} ➡ ${stock.getPrice2300().toMoney(stock)}"

                        priceChangeAbsoluteView.text = stock.changePriceArbShortAbsolute.toMoney(stock)
                        priceChangePercentView.text = stock.changePriceArbShortPercent.toPercent()

                        priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(stock.changePriceArbShortPercent))
                        priceChangePercentView.setTextColor(Utils.getColorForValue(stock.changePriceArbShortPercent))
                        priceView.setTextColor(Utils.getColorForValue(stock.changePriceArbShortPercent))
                    }

                    itemView.setOnClickListener {
                        Utils.openOrderbookForStock(findNavController(), orderbookManager, stock)
                    }

                    chartButton.setOnClickListener {
                        Utils.openChartForStock(findNavController(), chartManager, stock)
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