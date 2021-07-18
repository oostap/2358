package com.project.ti2358.ui.strategy1000Buy

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
import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.manager.Strategy1000Buy
import com.project.ti2358.databinding.Fragment1000BuyStartBinding
import com.project.ti2358.databinding.Fragment1000BuyStartItemBinding
import com.project.ti2358.service.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.util.*

@KoinApiExtension
class Strategy1000BuyStartFragment : Fragment(R.layout.fragment_1000_buy_start) {
    val strategy1000Buy: Strategy1000Buy by inject()
    private val orderbookManager: OrderbookManager by inject()

    private var fragment1000BuyStartBinding: Fragment1000BuyStartBinding? = null

    var adapterList: Item1005RecyclerViewAdapter = Item1005RecyclerViewAdapter(emptyList())
    var stocks: MutableList<Stock> = mutableListOf()
    var numberSet: Int = 0

    override fun onDestroy() {
        fragment1000BuyStartBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = Fragment1000BuyStartBinding.bind(view)
        fragment1000BuyStartBinding = binding

        with(binding) {
            list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapterList

            startButton.setOnClickListener {
                if (strategy1000Buy.presetStocksSelected.isNotEmpty()) {
                    strategy1000Buy.currentNumberSet = numberSet
                    view.findNavController().navigate(R.id.action_nav_1000_buy_start_to_nav_1000_buy_finish)
                } else {
                    Utils.showErrorAlert(requireContext())
                }
            }

            updateButton.setOnClickListener {
                updateData()
            }

            set0Button.setOnClickListener {
                numberSet = 0
                updateData()
            }

            set1Button.setOnClickListener {
                numberSet = 1
                updateData()
            }

            set2Button.setOnClickListener {
                numberSet = 2
                updateData()
            }

            set3Button.setOnClickListener {
                numberSet = 3
                updateData()
            }

            set4Button.setOnClickListener {
                numberSet = 4
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
            strategy1000Buy.process(numberSet)
            stocks = strategy1000Buy.resort()
            if (search != "") stocks = Utils.search(stocks, search)
            adapterList.setData(stocks)
            updateTitle()

            fragment1000BuyStartBinding?.let {
                val colorDefault = Utils.DARK_BLUE
                val colorSelect = Utils.RED

                it.set0Button.setBackgroundColor(colorDefault)
                it.set1Button.setBackgroundColor(colorDefault)
                it.set2Button.setBackgroundColor(colorDefault)
                it.set3Button.setBackgroundColor(colorDefault)
                it.set4Button.setBackgroundColor(colorDefault)

                when (numberSet) {
                    0 -> it.set0Button.setBackgroundColor(colorSelect)
                    1 -> it.set1Button.setBackgroundColor(colorSelect)
                    2 -> it.set2Button.setBackgroundColor(colorSelect)
                    3 -> it.set3Button.setBackgroundColor(colorSelect)
                    4 -> it.set4Button.setBackgroundColor(colorSelect)
                    else -> { }
                }
            }
        }
    }

    private fun updateTitle() {
        if (isAdded) {
            val act = requireActivity() as AppCompatActivity
            act.supportActionBar?.title = "700/1000 BUY - $numberSet (${strategy1000Buy.presetStocksSelected.size} шт.)"
        }
    }

    inner class Item1005RecyclerViewAdapter(private var values: List<Stock>) : RecyclerView.Adapter<Item1005RecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<Stock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(Fragment1000BuyStartItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: Fragment1000BuyStartItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val stock = values[index]
                with(binding) {
                    chooseView.setOnCheckedChangeListener(null)
                    chooseView.isChecked = strategy1000Buy.isSelected(stock)

                    tickerView.text = "${index + 1}) ${stock.getTickerLove()}"
                    priceView.text = "${stock.getPrice2359String()} ➡ ${stock.getPriceString()}"

                    val volume = stock.getTodayVolume() / 1000f
                    volumeSharesView.text = "%.1fk".format(locale = Locale.US, volume)

                    val volumeCash = stock.dayVolumeCash / 1000f / 1000f
                    volumeCashView.text = "%.2fM$".format(locale = Locale.US, volumeCash)

                    priceChangeAbsoluteView.text = stock.changePrice2300DayAbsolute.toMoney(stock)
                    priceChangePercentView.text = stock.changePrice2300DayPercent.toPercent()

                    priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(stock.changePrice2300DayAbsolute))
                    priceChangePercentView.setTextColor(Utils.getColorForValue(stock.changePrice2300DayAbsolute))

                    chooseView.setOnCheckedChangeListener { _, checked ->
                        GlobalScope.launch {
                            strategy1000Buy.setSelected(stock, checked, numberSet)
                            withContext(Dispatchers.Main) {
                                updateTitle()
                            }
                        }
                    }

                    itemView.setOnClickListener {
                        Utils.openOrderbookForStock(it.findNavController(), orderbookManager, stock)
                    }

                    itemView.setBackgroundColor(Utils.getColorForIndex(index))
                }
            }
        }
    }
}