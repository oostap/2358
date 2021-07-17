package com.project.ti2358.ui.strategy1000Buy

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
import com.project.ti2358.data.manager.StockPurchase
import com.project.ti2358.data.manager.Strategy1000Buy
import com.project.ti2358.data.manager.SettingsManager
import com.project.ti2358.databinding.Fragment1000BuyFinishBinding
import com.project.ti2358.databinding.Fragment1000BuyFinishItemBinding
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class Strategy1000BuyFinishFragment : Fragment(R.layout.fragment_1000_buy_finish) {
    private val strategy1000Buy: Strategy1000Buy by inject()

    private var fragment1000BuyFinishBinding: Fragment1000BuyFinishBinding? = null

    var adapterList: Item1005RecyclerViewAdapter = Item1005RecyclerViewAdapter(emptyList())
    var positions: MutableList<StockPurchase> = mutableListOf()

    override fun onDestroy() {
        fragment1000BuyFinishBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = Fragment1000BuyFinishBinding.bind(view)
        fragment1000BuyFinishBinding = binding

        with(binding) {
            list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapterList

            ///////////////////////////////////////////////////////////////
            start700Button.setOnClickListener {
                if (SettingsManager.get1000BuyPurchaseVolume() <= 0) {
                    Utils.showMessageAlert(requireContext(), "В настройках не задана общая сумма покупки, раздел 1000 buy.")
                } else {
                    if (Utils.isServiceRunning(requireContext(), Strategy700BuyService::class.java)) {
                        requireContext().stopService(Intent(context, Strategy700BuyService::class.java))
                    } else {
                        if (strategy1000Buy.getTotalPurchasePieces() > 0) {
                            Utils.startService(requireContext(), Strategy700BuyService::class.java)
                        }
                    }
                }
                updateServiceButtonText700()
            }
            ///////////////////////////////////////////////////////////////
            start1000Button.setOnClickListener {
                if (SettingsManager.get1000BuyPurchaseVolume() <= 0) {
                    Utils.showMessageAlert(requireContext(), "В настройках не задана общая сумма покупки, раздел 1000 buy.")
                } else {
                    if (Utils.isServiceRunning(requireContext(), Strategy1000BuyService::class.java)) {
                        requireContext().stopService(Intent(context, Strategy1000BuyService::class.java))
                    } else {
                        if (strategy1000Buy.getTotalPurchasePieces() > 0) {
                            Utils.startService(requireContext(), Strategy1000BuyService::class.java)
                        }
                    }
                }
                updateServiceButtonText1000()
            }
        }
        updateServiceButtonText700()
        updateServiceButtonText1000()
        ///////////////////////////////////////////////////////////////

        positions = strategy1000Buy.processPrepare()
        adapterList.setData(positions)
        updateInfoText()
    }

    fun updateInfoText() {
        val time = "07:00:00.100ms или 10:00:00.100ms"

        val prepareText: String = TheApplication.application.applicationContext.getString(R.string.prepare_start_1000_buy_text)
        fragment1000BuyFinishBinding?.infoTextView?.text = String.format(
            prepareText,
            time,
            positions.size,
            strategy1000Buy.getTotalPurchaseString(strategy1000Buy.stocksToPurchase)
        )
    }

    private fun updateServiceButtonText700() {
        if (Utils.isServiceRunning(requireContext(), Strategy700BuyService::class.java)) {
            fragment1000BuyFinishBinding?.start700Button?.text = getString(R.string.stop_700)
        } else {
            fragment1000BuyFinishBinding?.start700Button?.text = getString(R.string.start_buy_700)
        }
    }

    private fun updateServiceButtonText1000() {
        if (Utils.isServiceRunning(requireContext(), Strategy1000BuyService::class.java)) {
            fragment1000BuyFinishBinding?.start1000Button?.text = getString(R.string.stop_1000)
        } else {
            fragment1000BuyFinishBinding?.start1000Button?.text = getString(R.string.start_buy_1000)
        }
    }

    inner class Item1005RecyclerViewAdapter(private var values: List<StockPurchase>) : RecyclerView.Adapter<Item1005RecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<StockPurchase>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(Fragment1000BuyFinishItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: Fragment1000BuyFinishItemBinding) : RecyclerView.ViewHolder(binding.root) {
            var watcher: TextWatcher? = null

            fun bind(index: Int) {
                val purchaseStock = values[index]

                with (binding) {
                    tickerView.text = "${index + 1}) ${purchaseStock.stock.getTickerLove()}"
                    priceView.text = "${purchaseStock.stock.getPrice2359String()} ➡ ${purchaseStock.stock.getPriceNow().toMoney(purchaseStock.stock)}"
                    priceView.setTextColor(Utils.getColorForValue(purchaseStock.stock.changePrice2300DayPercent))

                    refreshPercent(purchaseStock)

                    pricePlusButton.setOnClickListener {
                        purchaseStock.addPriceLimitPercent(0.05)
                        refreshPercent(purchaseStock)
                        lotsEditText.setText("${purchaseStock.lots}")
                        strategy1000Buy.saveSelectedStocks()
                    }

                    priceMinusButton.setOnClickListener {
                        purchaseStock.addPriceLimitPercent(-0.05)
                        refreshPercent(purchaseStock)
                        lotsEditText.setText("${purchaseStock.lots}")
                        strategy1000Buy.saveSelectedStocks()
                    }

                    lotsPlusButton.setOnClickListener {
                        purchaseStock.addLots(1)
                        refreshPercent(purchaseStock)
                        lotsEditText.setText("${purchaseStock.lots}")
                        strategy1000Buy.saveSelectedStocks()
                    }

                    lotsMinusButton.setOnClickListener {
                        purchaseStock.addLots(-1)
                        refreshPercent(purchaseStock)
                        lotsEditText.setText("${purchaseStock.lots}")
                        strategy1000Buy.saveSelectedStocks()
                    }

                    itemView.setBackgroundColor(Utils.getColorForIndex(index))
                    refreshPercent(purchaseStock)

                    lotsEditText.clearFocus()
                    lotsEditText.removeCallbacks {  }
                    lotsEditText.removeTextChangedListener(watcher)
                    lotsEditText.setText("${purchaseStock.lots}")
                    watcher = lotsEditText.addTextChangedListener { v ->
                        if (lotsEditText.hasFocus()) {
                            val value = try {
                                (v.toString()).toInt()
                            } catch (e: Exception) {
                                1
                            }
                            purchaseStock.lots = value
                            refreshPercent(purchaseStock)
                        }
                    }
                }
            }

            private fun refreshPercent(stockPurchase: StockPurchase) {
                with (binding) {
                    val percent = stockPurchase.percentLimitPriceChange

                    priceChangePercentView.text = percent.toPercent()
                    priceChangeAbsoluteView.text = stockPurchase.absoluteLimitPriceChange.toMoney(stockPurchase.stock)
                    priceChangeAbsoluteTotalView.text = (stockPurchase.absoluteLimitPriceChange * stockPurchase.lots).toMoney(stockPurchase.stock)

                    priceBuyView.text = "${stockPurchase.getLimitPriceDouble().toMoney(stockPurchase.stock)} > ${(stockPurchase.getLimitPriceDouble() * stockPurchase.lots).toMoney(stockPurchase.stock)}"

                    priceChangePercentView.setTextColor(Utils.getColorForValue(percent))
                    priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(percent))
                    priceChangeAbsoluteTotalView.setTextColor(Utils.getColorForValue(percent))

                    tickerView.text = "${stockPurchase.ticker} x ${stockPurchase.lots} = %.2f%%".format(stockPurchase.profitPercent)
                }
                updateInfoText()
            }
        }
    }
}