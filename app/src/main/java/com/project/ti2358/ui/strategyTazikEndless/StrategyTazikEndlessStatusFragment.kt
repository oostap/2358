package com.project.ti2358.ui.strategyTazikEndless

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.OrderbookManager
import com.project.ti2358.data.manager.StockPurchase
import com.project.ti2358.data.manager.StrategyTazikEndless
import com.project.ti2358.databinding.FragmentTazikEndlessStatusBinding
import com.project.ti2358.databinding.FragmentTazikEndlessStatusItemBinding
import com.project.ti2358.service.*
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class StrategyTazikEndlessStatusFragment : Fragment(R.layout.fragment_tazik_endless_status) {
    private val strategyTazikEndless: StrategyTazikEndless by inject()
    private val orderbookManager: OrderbookManager by inject()

    private var fragmentTazikEndlessStatusBinding: FragmentTazikEndlessStatusBinding? = null

    var adapterList: ItemTazikRecyclerViewAdapter = ItemTazikRecyclerViewAdapter(emptyList())
    lateinit var stockPurchases: MutableList<StockPurchase>

    override fun onDestroy() {
        fragmentTazikEndlessStatusBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentTazikEndlessStatusBinding.bind(view)
        fragmentTazikEndlessStatusBinding = binding

        with(binding) {
            list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapterList

            plusButton.setOnClickListener {
                strategyTazikEndless.addBasicPercentLimitPriceChange(1)
                updateData()
            }

            minusButton.setOnClickListener {
                strategyTazikEndless.addBasicPercentLimitPriceChange(-1)
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
        GlobalScope.launch(Dispatchers.Main) {
            fragmentTazikEndlessStatusBinding?.currentChangeView?.text = strategyTazikEndless.basicPercentLimitPriceChange.toPercent()
            stockPurchases = strategyTazikEndless.getSortedPurchases().toMutableList()
            if (search != "") stockPurchases = Utils.search(stockPurchases, search)
            adapterList.setData(stockPurchases)
            updateTitle()
        }
    }

    private fun updateTitle() {
        if (isAdded) {
            val act = requireActivity() as AppCompatActivity
            act.supportActionBar?.title = "Статус 🛁: ${strategyTazikEndless.stocksSelected.size} шт."
        }
    }

    inner class ItemTazikRecyclerViewAdapter(private var values: List<StockPurchase>) : RecyclerView.Adapter<ItemTazikRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<StockPurchase>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FragmentTazikEndlessStatusItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentTazikEndlessStatusItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val purchase = values[index]

                with(binding) {
                    tickerView.text = "${index + 1}) ${purchase.stock.getTickerLove()} ${purchase.percentLimitPriceChange.toPercent()}"
                    priceView.text = "${purchase.tazikEndlessPrice} ➡ ${purchase.stock.getPriceNow().toMoney(purchase.stock)}"

                    volumeSharesView.text = purchase.stock.getTodayVolume().toString()

                    var vol = 0
                    if (purchase.stock.minuteCandles.isNotEmpty()) {
                        vol = purchase.stock.minuteCandles.last().volume
                    }
                    volumeCashView.text = "$vol"

                    val changePercent = (100 * purchase.stock.getPriceNow()) / purchase.tazikEndlessPrice - 100
                    val changeAbsolute = purchase.stock.getPriceNow() - purchase.tazikEndlessPrice

                    priceChangeAbsoluteView.text = changeAbsolute.toMoney(purchase.stock)
                    priceChangePercentView.text = changePercent.toPercent()

                    priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(changePercent))
                    priceChangePercentView.setTextColor(Utils.getColorForValue(changePercent))

                    itemView.setOnClickListener {
                        Utils.openOrderbookForStock(it.findNavController(), orderbookManager, purchase.stock)
                    }

                    itemView.setBackgroundColor(Utils.getColorForIndex(index))
                }
            }
        }
    }
}