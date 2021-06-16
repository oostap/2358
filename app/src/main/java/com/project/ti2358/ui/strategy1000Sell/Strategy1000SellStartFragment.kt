package com.project.ti2358.ui.strategy1000Sell

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
import com.project.ti2358.data.manager.DepositManager
import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.manager.Strategy1000Sell
import com.project.ti2358.databinding.Fragment1000SellStartBinding
import com.project.ti2358.databinding.Fragment1000SellStartItemBinding
import com.project.ti2358.service.Utils
import com.project.ti2358.service.toMoney
import com.project.ti2358.service.toPercent
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class Strategy1000SellStartFragment : Fragment(R.layout.fragment_1000_sell_start) {
    val strategy1000Sell: Strategy1000Sell by inject()
    val depositManager: DepositManager by inject()

    private var fragment1000SellStartBinding: Fragment1000SellStartBinding? = null

    var adapterList: Item1000SellRecyclerViewAdapter = Item1000SellRecyclerViewAdapter(emptyList())
    var jobUpdate: Job? = null
    var stocks: MutableList<Stock> = mutableListOf()
    var numberSet: Int = 1

    override fun onDestroy() {
        jobUpdate?.cancel()
        fragment1000SellStartBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = Fragment1000SellStartBinding.bind(view)
        fragment1000SellStartBinding = binding

        with(binding) {
            list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapterList

            startButton.setOnClickListener {
                if (strategy1000Sell.presetStocksSelected.isNotEmpty()) {
                    strategy1000Sell.currentNumberSet = numberSet
                    view.findNavController().navigate(R.id.action_nav_1000_sell_start_to_nav_1000_sell_finish)
                } else {
                    Utils.showErrorAlert(requireContext())
                }
            }

            updateButton.setOnClickListener {
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

        jobUpdate?.cancel()
        jobUpdate = GlobalScope.launch(Dispatchers.Main) {
            depositManager.refreshDeposit()
            updateData()
        }
        updateData()
    }

    private fun updateData(search: String = "") {
        GlobalScope.launch(Dispatchers.Main) {
            strategy1000Sell.process(numberSet)
            if (search != "") {
                stocks = Utils.search(stocks, search)
            }
            stocks = strategy1000Sell.resort()
            adapterList.setData(stocks)
            updateTitle()

            fragment1000SellStartBinding?.let {
                val colorDefault = Utils.DARK_BLUE
                val colorSelect = Utils.RED

                it.set1Button.setBackgroundColor(colorDefault)
                it.set2Button.setBackgroundColor(colorDefault)
                it.set3Button.setBackgroundColor(colorDefault)

                when (numberSet) {
                    1 -> it.set1Button.setBackgroundColor(colorSelect)
                    2 -> it.set2Button.setBackgroundColor(colorSelect)
                    3 -> it.set3Button.setBackgroundColor(colorSelect)
                    else -> { }
                }
            }
        }
    }

    private fun updateTitle() {
        if (isAdded) {
            val act = requireActivity() as AppCompatActivity
            act.supportActionBar?.title = "700/1000 SELL - $numberSet (${strategy1000Sell.presetStocksSelected.size} шт.)"
        }
    }

    inner class Item1000SellRecyclerViewAdapter(private var values: List<Stock>) : RecyclerView.Adapter<Item1000SellRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<Stock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(Fragment1000SellStartItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: Fragment1000SellStartItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val stock = values[index]
                val portfolioPosition = depositManager.getPositionForFigi(stock.figi)

                with(binding) {
                    tickerView.text = "${index + 1}) ${stock.getTickerLove()}"
                    chooseView.setOnCheckedChangeListener(null)
                    chooseView.isChecked = strategy1000Sell.isSelected(stock)

                    chooseView.setOnCheckedChangeListener { _, checked ->
                        strategy1000Sell.setSelected(stock, checked, numberSet)
                        updateData()
                    }

                    itemView.setOnClickListener { _ ->
                        Utils.openTinkoffForTicker(requireContext(), stock.ticker)
                    }

                    if (portfolioPosition != null) { // если есть в депо
                        val avg = portfolioPosition.getAveragePrice()
                        volumeSharesView.text = "${portfolioPosition.lots} шт."
                        priceView.text = "${avg.toMoney(portfolioPosition.stock)} ➡ ${portfolioPosition.stock?.getPriceString()}"

                        val profit = portfolioPosition.getProfitAmount()
                        priceChangeAbsoluteView.text = profit.toMoney(portfolioPosition.stock)

                        var totalCash = portfolioPosition.balance * avg
                        val percent = portfolioPosition.getProfitPercent()
                        priceChangePercentView.text = percent.toPercent()

                        totalCash += profit
                        volumeCashView.text = totalCash.toMoney(portfolioPosition.stock)

                        priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(profit))
                        priceChangePercentView.setTextColor(Utils.getColorForValue(profit))
                        priceView.setTextColor(Utils.getColorForValue(profit))

                    } else { // нет в депо
                        priceView.text = "${stock.getPrice2359String()} ➡ ${stock.getPriceString()}"

                        volumeSharesView.text = "0 шт."
                        volumeCashView.text = ""

                        priceChangeAbsoluteView.text = stock.changePrice2300DayAbsolute.toMoney(stock)
                        priceChangePercentView.text = stock.changePrice2300DayPercent.toPercent()

                        priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(stock.changePrice2300DayAbsolute))
                        priceChangePercentView.setTextColor(Utils.getColorForValue(stock.changePrice2300DayAbsolute))
                        priceView.setTextColor(Utils.getColorForValue(stock.changePrice2300DayAbsolute))
                    }

                    itemView.setBackgroundColor(Utils.getColorForIndex(index))
                }
            }
        }
    }
}