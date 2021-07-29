package com.project.ti2358.ui.strategyTazik

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.StockPurchase
import com.project.ti2358.data.manager.SettingsManager
import com.project.ti2358.data.manager.StockManager
import com.project.ti2358.data.manager.StrategyTazik
import com.project.ti2358.databinding.FragmentTazikStatusBinding
import com.project.ti2358.databinding.FragmentTazikStatusItemBinding
import com.project.ti2358.service.*
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class StrategyTazikStatusFragment : Fragment(R.layout.fragment_tazik_status) {
    val strategyTazik: StrategyTazik by inject()

    private var fragmentTazikStatusBinding: FragmentTazikStatusBinding? = null

    var adapterList: ItemTazikRecyclerViewAdapter = ItemTazikRecyclerViewAdapter(emptyList())
    lateinit var stockPurchases: MutableList<StockPurchase>

    override fun onDestroy() {
        fragmentTazikStatusBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentTazikStatusBinding.bind(view)
        fragmentTazikStatusBinding = binding

        with(binding) {
            list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapterList

            plusButton.setOnClickListener {
                strategyTazik.addBasicPercentLimitPriceChange(1)
                updateData()
            }

            minusButton.setOnClickListener {
                strategyTazik.addBasicPercentLimitPriceChange(-1)
                updateData()
            }

            updateButton.setOnClickListener {
                updateData()
            }

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
                updateData()
                false
            }
        }

        updateData()
    }

    private fun updateData(search: String = "") {
        GlobalScope.launch(StockManager.stockContext) {
            stockPurchases = strategyTazik.getSortedPurchases().toMutableList()
            if (search != "") stockPurchases = Utils.search(stockPurchases, search)

            withContext(Dispatchers.Main) {
                fragmentTazikStatusBinding?.currentChangeView?.text = strategyTazik.basicPercentLimitPriceChange.toPercent()
                adapterList.setData(stockPurchases)
                updateTitle()
            }
        }
    }

    private fun updateTitle() {
        if (isAdded) {
            val act = requireActivity() as AppCompatActivity
            act.supportActionBar?.title = "Статус 🛁: ${strategyTazik.stocksSelected.size} шт."
        }
    }

    inner class ItemTazikRecyclerViewAdapter(private var values: List<StockPurchase>) : RecyclerView.Adapter<ItemTazikRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<StockPurchase>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FragmentTazikStatusItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentTazikStatusItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val purchase = values[index]

                with(binding) {
                    tickerView.text = "${index + 1}) ${purchase.stock.getTickerLove()} ${purchase.percentLimitPriceChange.toPercent()}"
                    priceView.text = "${purchase.tazikPrice.toMoney(purchase.stock)} ➡ ${purchase.stock.getPriceNow().toMoney(purchase.stock)}"

                    volumeSharesView.text = purchase.stock.getTodayVolume().toString()

                    var vol = 0
                    if (purchase.stock.minuteCandles.isNotEmpty()) {
                        vol = purchase.stock.minuteCandles.last().volume
                    }
                    volumeCashView.text = "$vol"

                    val volume = SettingsManager.getTazikEndlessMinVolume()
                    val changePercent = (100 * purchase.stock.getPriceNow()) / purchase.tazikPrice - 100
                    val changeAbsolute = purchase.stock.getPriceNow() - purchase.tazikPrice

                    priceChangeAbsoluteView.text = changeAbsolute.toMoney(purchase.stock)
                    priceChangePercentView.text = changePercent.toPercent()

                    priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(changePercent))
                    priceChangePercentView.setTextColor(Utils.getColorForValue(changePercent))

                    itemView.setOnClickListener {
                        Utils.openTinkoffForTicker(requireContext(), purchase.ticker)
                    }

                    if (SettingsManager.getBrokerAlor() && SettingsManager.getBrokerTinkoff()) {
                        itemView.setBackgroundColor(Utils.getColorForBrokerValue(purchase.broker))
                    } else {
                        itemView.setBackgroundColor(Utils.getColorForIndex(index))
                    }
                }
            }
        }
    }
}