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
import com.project.ti2358.data.common.BrokerType
import com.project.ti2358.data.manager.*
import com.project.ti2358.databinding.Fragment1000BuyStartBinding
import com.project.ti2358.databinding.Fragment1000BuyStartItemBinding
import com.project.ti2358.databinding.Fragment1000SellStartItemBinding
import com.project.ti2358.service.*
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.util.*

@KoinApiExtension
class Strategy1000BuyStartFragment : Fragment(R.layout.fragment_1000_buy_start) {
    val strategy1000Buy: Strategy1000Buy by inject()
    val brokerManager: BrokerManager by inject()
    private val orderbookManager: OrderbookManager by inject()

    private var fragment1000BuyStartBinding: Fragment1000BuyStartBinding? = null

    var adapterListDepo = Item1000BuyDepoRecyclerViewAdapter(emptyList())
    var adapterListLong = Item1000BuyLongRecyclerViewAdapter(emptyList())

    var stocks: MutableList<Stock> = mutableListOf()
    var purchases: List<StockPurchase> = mutableListOf()
    var numberSet: Int = 0
    var jobUpdate: Job? = null

    override fun onDestroy() {
        jobUpdate?.cancel()
        fragment1000BuyStartBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = Fragment1000BuyStartBinding.bind(view)
        fragment1000BuyStartBinding = binding

        with(binding) {
            listLong.addItemDecoration(DividerItemDecoration(listLong.context, DividerItemDecoration.VERTICAL))
            listLong.layoutManager = LinearLayoutManager(context)
            listLong.adapter = adapterListLong

            listDepo.addItemDecoration(DividerItemDecoration(listDepo.context, DividerItemDecoration.VERTICAL))
            listDepo.layoutManager = LinearLayoutManager(context)
            listDepo.adapter = adapterListDepo

            startButton.setOnClickListener {
                strategy1000Buy.currentNumberSet = numberSet
                if (strategy1000Buy.presetStocksSelected.isEmpty() && numberSet != 0 || (numberSet == 0 && strategy1000Buy.purchaseFromPortfolioSelected.isEmpty())) {
                    Utils.showErrorAlert(requireContext())
                } else {
                    view.findNavController().navigate(R.id.action_nav_1000_buy_start_to_nav_1000_buy_finish)
                }
            }

            updateButton.setOnClickListener {
                if (numberSet == 0) {
                    updateDataDepo()
                } else {
                    updateDataLong()
                }
            }

            set0Button.setOnClickListener {
                numberSet = 0
                strategy1000Buy.presetStocksSelected.clear()
                updateDataDepo()
            }

            set1Button.setOnClickListener {
                numberSet = 1
                updateDataLong()
            }

            set2Button.setOnClickListener {
                numberSet = 2
                updateDataLong()
            }

            set3Button.setOnClickListener {
                numberSet = 3
                updateDataLong()
            }

            set4Button.setOnClickListener {
                numberSet = 4
                updateDataLong()
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
                    updateDataLong(text)
                }
            })
            searchView.setOnCloseListener {
                updateDataLong()
                false
            }
        }

        jobUpdate?.cancel()
        jobUpdate = GlobalScope.launch(Dispatchers.Main) {
            brokerManager.refreshDeposit()
            if (numberSet == 0) {
                updateDataDepo()
            } else {
                updateDataLong()
            }
        }
    }

    private fun updateDataDepo() {
        fragment1000BuyStartBinding?.apply {
            listLong.visibility = View.GONE
            searchView.visibility = View.GONE
            listDepo.visibility = View.VISIBLE
        }

        GlobalScope.launch(Dispatchers.Main) {
            purchases = strategy1000Buy.processPortfolio()
            adapterListDepo.setData(purchases)
            updateTitle()
            updateButtons()
        }
    }

    private fun updateDataLong(search: String = "") {
        fragment1000BuyStartBinding?.apply {
            listLong.visibility = View.VISIBLE
            searchView.visibility = View.VISIBLE
            listDepo.visibility = View.GONE
        }

        GlobalScope.launch(Dispatchers.Main) {
            strategy1000Buy.processLong(numberSet)
            stocks = strategy1000Buy.resort()
            if (search != "") stocks = Utils.search(stocks, search)
            adapterListLong.setData(stocks)

            updateTitle()
            updateButtons()
        }
    }

    private fun updateButtons() {
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
                act.supportActionBar?.title = "700/1000 BUY - $numberSet (${strategy1000Buy.presetStocksSelected.size} шт.)"
            }
        }
    }

    inner class Item1000BuyLongRecyclerViewAdapter(private var values: List<Stock>) : RecyclerView.Adapter<Item1000BuyLongRecyclerViewAdapter.ViewHolder>() {
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
                    chooseView.isChecked = strategy1000Buy.isSelectedLong(stock)

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
                            strategy1000Buy.setSelectedLong(stock, checked, numberSet)
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

    inner class Item1000BuyDepoRecyclerViewAdapter(private var values: List<StockPurchase>) : RecyclerView.Adapter<Item1000BuyDepoRecyclerViewAdapter.ViewHolder>() {
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
                    chooseView.isChecked = strategy1000Buy.isSelectedPortfolio(purchase)

                    chooseView.setOnCheckedChangeListener { _, checked ->
                        GlobalScope.launch {
                            strategy1000Buy.setSelectedPortfolio(purchase, checked)
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