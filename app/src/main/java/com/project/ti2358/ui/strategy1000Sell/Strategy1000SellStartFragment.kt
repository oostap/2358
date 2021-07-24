package com.project.ti2358.ui.strategy1000Sell

import android.opengl.Visibility
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
import com.project.ti2358.data.common.BrokerType
import com.project.ti2358.data.manager.*
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
    val brokerManager: BrokerManager by inject()

    val orderbookManager: OrderbookManager by inject()

    private var fragment1000SellStartBinding: Fragment1000SellStartBinding? = null

    var adapterListDepo = Item1000SellDepoRecyclerViewAdapter(emptyList())
    var adapterListShort = Item1000SellShortRecyclerViewAdapter(emptyList())

    var jobUpdate: Job? = null
    var stocks: List<Stock> = mutableListOf()
    var purchases: List<StockPurchase> = mutableListOf()
    var numberSet: Int = 0

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
            listShort.addItemDecoration(DividerItemDecoration(listShort.context, DividerItemDecoration.VERTICAL))
            listShort.layoutManager = LinearLayoutManager(context)
            listShort.adapter = adapterListShort

            listDepo.addItemDecoration(DividerItemDecoration(listDepo.context, DividerItemDecoration.VERTICAL))
            listDepo.layoutManager = LinearLayoutManager(context)
            listDepo.adapter = adapterListDepo

            startButton.setOnClickListener {
                strategy1000Sell.currentNumberSet = numberSet
                if (strategy1000Sell.presetStocksSelected.isEmpty() && numberSet != 0 || (numberSet == 0 && strategy1000Sell.purchaseFromPortfolioSelected.isEmpty())) {
                    Utils.showErrorAlert(requireContext())
                } else {
                    view.findNavController().navigate(R.id.action_nav_1000_sell_start_to_nav_1000_sell_finish)
                }
            }

            updateButton.setOnClickListener {
                if (numberSet == 0) {
                    updateDataDepo()
                } else {
                    updateDataShort()
                }
            }

            set0Button.setOnClickListener {
                numberSet = 0
                strategy1000Sell.presetStocksSelected.clear()
                updateDataDepo()
            }

            set1Button.setOnClickListener {
                numberSet = 1
                updateDataShort()
            }

            set2Button.setOnClickListener {
                numberSet = 2
                updateDataShort()
            }

            set3Button.setOnClickListener {
                numberSet = 3
                updateDataShort()
            }

            set4Button.setOnClickListener {
                numberSet = 4
                updateDataShort()
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
                    updateDataShort(text)
                }
            })
            searchView.setOnCloseListener {
                updateDataShort()
                false
            }
        }

        jobUpdate?.cancel()
        jobUpdate = GlobalScope.launch(Dispatchers.Main) {
            brokerManager.refreshDeposit()
            if (numberSet == 0) {
                updateDataDepo()
            } else {
                updateDataShort()
            }
        }
    }

    private fun updateDataDepo() {
        fragment1000SellStartBinding?.apply {
            listShort.visibility = View.GONE
            searchView.visibility = View.GONE
            listDepo.visibility = View.VISIBLE
        }

        GlobalScope.launch(Dispatchers.Main) {
            purchases = strategy1000Sell.processPortfolio()
            adapterListDepo.setData(purchases)
            updateTitle()
            updateButtons()
        }
    }

    private fun updateDataShort(search: String = "") {
        fragment1000SellStartBinding?.apply {
            listShort.visibility = View.VISIBLE
            searchView.visibility = View.VISIBLE
            listDepo.visibility = View.GONE
        }

        GlobalScope.launch(Dispatchers.Main) {
            strategy1000Sell.processShort(numberSet)
            stocks = strategy1000Sell.resort()
            if (search != "") stocks = Utils.search(stocks, search)
            adapterListShort.setData(stocks)
            updateTitle()
            updateButtons()
        }
    }

    private fun updateButtons() {
        fragment1000SellStartBinding?.let {
            val colorDefault = Utils.DARK_BLUE
            val colorSelect = Utils.RED

            it.set0Button.setBackgroundColor(colorDefault)
            it.set1Button.setBackgroundColor(colorDefault)
            it.set2Button.setBackgroundColor(colorDefault)
            it.set3Button.setBackgroundColor(colorDefault)

            when (numberSet) {
                0 -> it.set0Button.setBackgroundColor(colorSelect)
                1 -> it.set1Button.setBackgroundColor(colorSelect)
                2 -> it.set2Button.setBackgroundColor(colorSelect)
                3 -> it.set3Button.setBackgroundColor(colorSelect)
                else -> { }
            }
        }
    }

    private fun updateTitle() {
        if (isAdded) {
            val act = requireActivity() as AppCompatActivity
            if (numberSet == 0) {
                val ti = brokerManager.getPositionsAll(BrokerType.TINKOFF)
                val alor = brokerManager.getPositionsAll(BrokerType.ALOR)
                var text = "700/1000 SELL"

                if (ti.isNotEmpty()) {
                    text += " ТИ=${ti.size}"
                }
                if (alor.isNotEmpty()) {
                    text += " ALOR=${alor.size}"
                }

                act.supportActionBar?.title = text
            } else {
                act.supportActionBar?.title = "700/1000 SHORT ⚠️ - $numberSet (${strategy1000Sell.presetStocksSelected.size} шт.)"
            }
        }
    }

    inner class Item1000SellShortRecyclerViewAdapter(private var values: List<Stock>) : RecyclerView.Adapter<Item1000SellShortRecyclerViewAdapter.ViewHolder>() {
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

                with(binding) {
                    tickerView.text = "${index + 1}) ${stock.getTickerLove()}"
                    chooseView.setOnCheckedChangeListener(null)
                    chooseView.isChecked = strategy1000Sell.isSelectedShort(stock)

                    chooseView.setOnCheckedChangeListener { _, checked ->
                        GlobalScope.launch {
                            strategy1000Sell.setSelectedShort(stock, checked, numberSet)
                            withContext(Dispatchers.Main) {
                                updateTitle()
                            }
                        }
                    }

                    itemView.setOnClickListener { v ->
                        Utils.openOrderbookForStock(v.findNavController(), orderbookManager, stock)
                    }

                    priceView.text = "${stock.getPrice2359String()} ➡ ${stock.getPriceString()}"

                    volumeSharesView.text = "0 шт."
                    volumeCashView.text = ""

                    priceChangeAbsoluteView.text = stock.changePrice2300DayAbsolute.toMoney(stock)
                    priceChangePercentView.text = stock.changePrice2300DayPercent.toPercent()

                    priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(stock.changePrice2300DayAbsolute))
                    priceChangePercentView.setTextColor(Utils.getColorForValue(stock.changePrice2300DayAbsolute))
                    priceView.setTextColor(Utils.getColorForValue(stock.changePrice2300DayAbsolute))

                    itemView.setBackgroundColor(Utils.getColorForIndex(index))
                }
            }
        }
    }

    inner class Item1000SellDepoRecyclerViewAdapter(private var values: List<StockPurchase>) : RecyclerView.Adapter<Item1000SellDepoRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<StockPurchase>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(Fragment1000SellStartItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: Fragment1000SellStartItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val purchase = values[index]
                val stock = purchase.stock
                val portfolioPosition = brokerManager.getPositionForStock(stock, purchase.broker)

                with(binding) {
                    tickerView.text = "${index + 1}) ${stock.getTickerLove()}"
                    chooseView.setOnCheckedChangeListener(null)
                    chooseView.isChecked = strategy1000Sell.isSelectedPortfolio(purchase)

                    chooseView.setOnCheckedChangeListener { _, checked ->
                        GlobalScope.launch {
                            strategy1000Sell.setSelectedPortfolio(purchase, checked)
                        }
                    }

                    itemView.setOnClickListener { v ->
                        Utils.openOrderbookForStock(v.findNavController(), orderbookManager, stock)
                    }

                    if (portfolioPosition != null) { // если есть в депо
                        val avg = portfolioPosition.getAveragePrice()
                        volumeSharesView.text = "${portfolioPosition.getLots()} шт."
                        priceView.text = "${avg.toMoney(stock)} ➡ ${stock.getPriceString()}"

                        val profit = portfolioPosition.getProfitAmount()
                        priceChangeAbsoluteView.text = profit.toMoney(stock)

                        var totalCash = portfolioPosition.getLots() * avg
                        val percent = portfolioPosition.getProfitPercent()
                        priceChangePercentView.text = percent.toPercent()

                        totalCash += profit
                        volumeCashView.text = totalCash.toMoney(stock)

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