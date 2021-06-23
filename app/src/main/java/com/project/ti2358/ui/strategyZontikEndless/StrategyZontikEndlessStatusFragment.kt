package com.project.ti2358.ui.strategyZontikEndless

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
import com.project.ti2358.data.manager.PurchaseStock
import com.project.ti2358.data.manager.SettingsManager
import com.project.ti2358.data.manager.StrategyZontikEndless
import com.project.ti2358.databinding.FragmentZontikEndlessStatusBinding
import com.project.ti2358.databinding.FragmentZontikEndlessStatusItemBinding
import com.project.ti2358.service.*
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class StrategyZontikEndlessStatusFragment : Fragment(R.layout.fragment_zontik_endless_status) {
    private val strategyZontikEndless: StrategyZontikEndless by inject()

    private var fragmentZontikEndlessStatusBinding: FragmentZontikEndlessStatusBinding? = null

    var adapterList: ItemTazikRecyclerViewAdapter = ItemTazikRecyclerViewAdapter(emptyList())
    lateinit var stocks: MutableList<PurchaseStock>

    override fun onDestroy() {
        fragmentZontikEndlessStatusBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentZontikEndlessStatusBinding.bind(view)
        fragmentZontikEndlessStatusBinding = binding

        with(binding) {
            list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapterList

            plusButton.setOnClickListener {
                strategyZontikEndless.addBasicPercentLimitPriceChange(1)
                updateData()
            }

            minusButton.setOnClickListener {
                strategyZontikEndless.addBasicPercentLimitPriceChange(-1)
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
            fragmentZontikEndlessStatusBinding?.currentChangeView?.text = strategyZontikEndless.basicPercentLimitPriceChange.toPercent()
            stocks = strategyZontikEndless.getSortedPurchases().toMutableList()
            if (search != "") stocks = Utils.search(stocks, search)
            adapterList.setData(stocks)
            updateTitle()
        }
    }

    private fun updateTitle() {
        if (isAdded) {
            val act = requireActivity() as AppCompatActivity
            act.supportActionBar?.title = "Статус: ${strategyZontikEndless.stocksSelected.size} шт."
        }
    }

    inner class ItemTazikRecyclerViewAdapter(private var values: List<PurchaseStock>) : RecyclerView.Adapter<ItemTazikRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<PurchaseStock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FragmentZontikEndlessStatusItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentZontikEndlessStatusItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val purchase = values[index]

                with(binding) {
                    tickerView.text = "${index + 1}) ${purchase.stock.getTickerLove()} ${purchase.percentLimitPriceChange.toPercent()}"
                    priceView.text = "${purchase.zontikEndlessPrice} ➡ ${purchase.stock.getPriceNow().toMoney(purchase.stock)}"

                    val volumeToday = purchase.stock.getTodayVolume() / 1000f
                    volumeSharesView.text = "%.1fk".format(volumeToday)

                    var vol = 0
                    if (purchase.stock.minuteCandles.isNotEmpty()) {
                        vol = purchase.stock.minuteCandles.last().volume
                    }
                    volumeCashView.text = "$vol"

                    val changePercent = (100 * purchase.stock.getPriceNow()) / purchase.zontikEndlessPrice - 100
                    val changeAbsolute = purchase.stock.getPriceNow() - purchase.zontikEndlessPrice

                    priceChangeAbsoluteView.text = changeAbsolute.toMoney(purchase.stock)
                    priceChangePercentView.text = changePercent.toPercent()

                    priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(changePercent))
                    priceChangePercentView.setTextColor(Utils.getColorForValue(changePercent))

                    itemView.setOnClickListener {
                        Utils.openTinkoffForTicker(requireContext(), purchase.ticker)
                    }

                    itemView.setBackgroundColor(Utils.getColorForIndex(index))
                }
            }
        }
    }
}