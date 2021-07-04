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
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.util.*

@KoinApiExtension
class ArbitrationFragment : Fragment(R.layout.fragment_arbitration) {
    val orderbookManager: OrderbookManager by inject()
    val chartManager: ChartManager by inject()
    val strategyArbitration: StrategyArbitration by inject()

    private var fragmentArbitrationBinding: FragmentArbitrationBinding? = null

    var adapterList: ItemRocketRecyclerViewAdapter = ItemRocketRecyclerViewAdapter(emptyList())
    var long: Boolean = true

    override fun onDestroy() {
        fragmentArbitrationBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentArbitrationBinding.bind(view)
        fragmentArbitrationBinding = binding

        with(binding) {
            list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapterList

            startButton.setOnClickListener {
                if (Utils.isServiceRunning(requireContext(), StrategyArbitrationService::class.java)) {
                    requireContext().stopService(Intent(context, StrategyArbitrationService::class.java))
                } else {
                    Utils.startService(requireContext(), StrategyArbitrationService::class.java)
                }
                updateServiceButtonText()
            }
            updateServiceButtonText()

            longButton.setOnClickListener {
                updateData(true)
            }

            shortButton.setOnClickListener {
                updateData(false)
            }
        }

        updateData(true)

    }

    private fun updateData(isLong: Boolean) {
        long = isLong

        fragmentArbitrationBinding?.apply {
            val colorDefault = Utils.DARK_BLUE
            val colorSelect = Utils.RED

            longButton.setBackgroundColor(colorDefault)
            shortButton.setBackgroundColor(colorDefault)

            if (long) {
                adapterList.setData(strategyArbitration.longStocks)
                longButton.setBackgroundColor(colorSelect)
            } else {
                adapterList.setData(strategyArbitration.shortStocks)
                shortButton.setBackgroundColor(colorSelect)
            }
            updateTitle()
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

    inner class ItemRocketRecyclerViewAdapter(private var values: List<StockArbitration>) : RecyclerView.Adapter<ItemRocketRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<StockArbitration>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FragmentArbitrationItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentArbitrationItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val arbStock = values[index]
                with(binding) {
                    tickerView.text = "${index + 1}) ${arbStock.stock.getTickerLove()}"

                    val deltaMinutes = ((Calendar.getInstance().time.time - arbStock.fireTime) / 60.0 / 1000.0).toInt()
                    minutesView.text = "$deltaMinutes мин"

                    val volume = arbStock.stock.getTodayVolume() / 1000f
                    volumeSharesView.text = "%.1fk".format(volume)

                    if (arbStock.long) {
                        priceView.text = "${arbStock.askRU.toMoney(arbStock.stock)} ➡ ${arbStock.priceUS.toMoney(arbStock.stock)}"
                    } else {
                        priceView.text = "${arbStock.bidRU.toMoney(arbStock.stock)} ➡ ${arbStock.priceUS.toMoney(arbStock.stock)}"
                    }

                    priceChangeAbsoluteView.text = arbStock.changePriceAbsolute.toMoney(arbStock.stock)
                    priceChangePercentView.text = arbStock.changePricePercent.toPercent()

                    priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(arbStock.changePricePercent))
                    priceChangePercentView.setTextColor(Utils.getColorForValue(arbStock.changePricePercent))
                    priceView.setTextColor(Utils.getColorForValue(arbStock.changePricePercent))

                    itemView.setOnClickListener {
                        Utils.openOrderbookForStock(findNavController(), orderbookManager, arbStock.stock)
                    }

                    chartButton.setOnClickListener {
                        Utils.openChartForStock(findNavController(), chartManager, arbStock.stock)
                    }

                    sectorView.text = arbStock.stock.getSectorName()
                    sectorView.setTextColor(Utils.getColorForSector(arbStock.stock.closePrices?.sector))

                    if (arbStock.stock.report != null) {
                        reportInfoView.text = arbStock.stock.getReportInfo()
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