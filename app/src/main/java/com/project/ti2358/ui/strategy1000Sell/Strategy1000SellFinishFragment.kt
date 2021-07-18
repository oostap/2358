package com.project.ti2358.ui.strategy1000Sell

import android.content.Intent
import android.os.Bundle
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.TheApplication
import com.project.ti2358.data.manager.SettingsManager
import com.project.ti2358.data.manager.StockPurchase
import com.project.ti2358.data.manager.Strategy1000Sell
import com.project.ti2358.databinding.Fragment1000SellFinishBinding
import com.project.ti2358.databinding.Fragment1000SellFinishItemBinding
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class Strategy1000SellFinishFragment : Fragment(R.layout.fragment_1000_sell_finish) {
    private val strategy1000Sell: Strategy1000Sell by inject()

    private var fragment1000SellFinishBinding: Fragment1000SellFinishBinding? = null

    var adapterListSell: Item1000SellRecyclerViewAdapter = Item1000SellRecyclerViewAdapter(emptyList())
    var positions: MutableList<StockPurchase> = mutableListOf()

    override fun onDestroy() {
        fragment1000SellFinishBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = Fragment1000SellFinishBinding.bind(view)
        fragment1000SellFinishBinding = binding

        with(binding) {
            list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapterListSell

            ///////////////////////////////////////////////////////////////////
            start1000Button.setOnClickListener {
                if (Utils.isServiceRunning(requireContext(), Strategy1000SellService::class.java)) {
                    requireContext().stopService(Intent(context, Strategy1000SellService::class.java))
                } else {
                    if (strategy1000Sell.getTotalPurchasePieces() > 0) {
                        Utils.startService(requireContext(), Strategy1000SellService::class.java)
                    }
                }
                updateServiceButtonText1000()
            }
            updateServiceButtonText1000()
            //////////////////////////////////////////////////////////////////
            start700Button.setOnClickListener {
                if (Utils.isServiceRunning(requireContext(), Strategy700SellService::class.java)) {
                    requireContext().stopService(Intent(context, Strategy700SellService::class.java))
                } else {
                    if (strategy1000Sell.getTotalPurchasePieces() > 0) {
                        Utils.startService(requireContext(), Strategy700SellService::class.java)
                    }
                }
                updateServiceButtonText700()
            }
            updateServiceButtonText700()
        }
        positions = strategy1000Sell.processPrepare()
        adapterListSell.setData(positions)
        updateInfoText()
    }

    private fun updateServiceButtonText1000() {
        if (Utils.isServiceRunning(requireContext(), Strategy1000SellService::class.java)) {
            fragment1000SellFinishBinding?.start1000Button?.text = getString(R.string.stop_1000)
        } else {
            fragment1000SellFinishBinding?.start1000Button?.text = getString(R.string.start_sell_1000)
        }
    }

    private fun updateServiceButtonText700() {
        if (Utils.isServiceRunning(requireContext(), Strategy700SellService::class.java)) {
            fragment1000SellFinishBinding?.start700Button?.text = getString(R.string.stop_700)
        } else {
            fragment1000SellFinishBinding?.start700Button?.text = getString(R.string.start_sell_700)
        }
    }

    fun updateInfoText() {
        val time = "07:00:00.100ms или 10:00:00.100ms"
        val prepareText: String =
            TheApplication.application.applicationContext.getString(R.string.prepare_start_1000_sell_text)
        fragment1000SellFinishBinding?.infoTextView?.text = String.format(
            prepareText,
            time,
            positions.size,
            strategy1000Sell.getTotalSellString()
        )
    }

    inner class Item1000SellRecyclerViewAdapter(private var values: List<StockPurchase>) : RecyclerView.Adapter<Item1000SellRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<StockPurchase>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(Fragment1000SellFinishItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: Fragment1000SellFinishItemBinding) : RecyclerView.ViewHolder(binding.root) {
            var watcher: TextWatcher? = null

            fun bind(index: Int) {
                val purchase = values[index]

                with(binding) {
                    var deltaLots = 1
                    purchase.position?.let {
                        val avg = it.getAveragePrice()

                        if (it.getLots() > 20) {
                            deltaLots = (it.getLots() * 0.05).toInt()
                        }

                        tickerView.text = "${it.getPositionStock()?.ticker} x ${it.getLots()}"

                        val profit = it.getProfitAmount()
                        var totalCash = it.getLots() * avg
                        val percent = it.getProfitPercent()
                        percentProfitView.text = percent.toPercent()

                        totalCash += profit
                        priceView.text = "${avg.toMoney(purchase.stock)} ➡ ${totalCash.toMoney(purchase.stock)}"

                        priceProfitTotalView.text = profit.toMoney(purchase.stock)
                        priceProfitView.text = (profit / it.getLots()).toMoney(purchase.stock)

                        priceView.setTextColor(Utils.getColorForValue(percent))
                        percentProfitView.setTextColor(Utils.getColorForValue(percent))
                        priceProfitView.setTextColor(Utils.getColorForValue(percent))
                        priceProfitTotalView.setTextColor(Utils.getColorForValue(percent))
                    }

                    if (purchase.position == null) {
                        tickerView.text = "${purchase.ticker} x ${0}"
                        priceView.text = "${purchase.stock.getPrice2359String()} ➡ ${purchase.stock.getPriceString()}"

                        percentProfitView.text = ""
                        priceProfitView.text = ""
                        priceProfitTotalView.text = ""
                    }

                    percentPlusButton.setOnClickListener {
                        purchase.addPriceLimitPercent(0.05)
                        refreshPercent(purchase)
                        lotsEditText.setText("${purchase.lots}")
                        strategy1000Sell.saveSelectedStocks()
                    }

                    percentMinusButton.setOnClickListener {
                        purchase.addPriceLimitPercent(-0.05)
                        refreshPercent(purchase)
                        lotsEditText.setText("${purchase.lots}")
                        strategy1000Sell.saveSelectedStocks()
                    }

                    lotsPlusButton.setOnClickListener {
                        purchase.addLots(deltaLots)
                        refreshPercent(purchase)
                        lotsEditText.setText("${purchase.lots}")
                        strategy1000Sell.saveSelectedStocks()
                    }

                    lotsMinusButton.setOnClickListener {
                        purchase.addLots(-deltaLots)
                        refreshPercent(purchase)
                        lotsEditText.setText("${purchase.lots}")
                        strategy1000Sell.saveSelectedStocks()
                    }

                    refreshPercent(purchase)

                    lotsEditText.clearFocus()
                    lotsEditText.removeCallbacks {  }
                    lotsEditText.removeTextChangedListener(watcher)
                    lotsEditText.setText("${purchase.lots}")
                    watcher = lotsEditText.addTextChangedListener { v ->
                        if (lotsEditText.hasFocus()) {
                            val value = try {
                                (v.toString()).toInt()
                            } catch (e: Exception) {
                                1
                            }
                            purchase.lots = value
                            refreshPercent(purchase)
                        }
                    }

                    if (SettingsManager.getBrokerAlor() && SettingsManager.getBrokerTinkoff()) {
                        itemView.setBackgroundColor(Utils.getColorForBrokerValue(purchase.broker))
                    } else {
                        itemView.setBackgroundColor(Utils.getColorForIndex(index))
                    }
                }
            }

            private fun refreshPercent(stockPurchase: StockPurchase) {
                with(binding) {
                    percentProfitFutureView.text = stockPurchase.percentLimitPriceChange.toPercent()
                    percentProfitFutureView.setTextColor(Utils.getColorForValue(stockPurchase.percentLimitPriceChange))

                    val sellPrice = stockPurchase.getLimitPriceDouble()
                    val totalSellPrice = sellPrice * stockPurchase.lots
                    priceTotalView.text = "${sellPrice.toMoney(stockPurchase.stock)} ➡ ${totalSellPrice.toMoney(stockPurchase.stock)}"
                    priceTotalView.setTextColor(Utils.getColorForValue(stockPurchase.percentLimitPriceChange))

                    priceProfitFutureView.text = ""
                    percentProfitFutureTotalView.text = ""

                    tickerView.text = "${stockPurchase.ticker} x ${stockPurchase.lots} = %.2f%%".format(stockPurchase.profitPercent)

                    stockPurchase.position?.let {
                        val futureProfitPrice = sellPrice - it.getAveragePrice()
                        priceProfitFutureView.text = futureProfitPrice.toMoney(stockPurchase.stock)
                        percentProfitFutureTotalView.text = (futureProfitPrice * stockPurchase.lots).toMoney(stockPurchase.stock)

                        priceTotalView.setTextColor(Utils.getColorForValue(futureProfitPrice))
                        percentProfitFutureView.setTextColor(Utils.getColorForValue(futureProfitPrice))
                        priceProfitFutureView.setTextColor(Utils.getColorForValue(futureProfitPrice))
                        percentProfitFutureTotalView.setTextColor(Utils.getColorForValue(futureProfitPrice))
                    }
                }
                updateInfoText()
            }
        }
    }
}