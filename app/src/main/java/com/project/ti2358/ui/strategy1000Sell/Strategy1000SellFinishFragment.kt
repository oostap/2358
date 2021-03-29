package com.project.ti2358.ui.strategy1000Sell

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.TheApplication
import com.project.ti2358.data.manager.PurchaseStock
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
    var positions: MutableList<PurchaseStock> = mutableListOf()

    override fun onDestroy() {
        fragment1000SellFinishBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = Fragment1000SellFinishBinding.bind(view)
        fragment1000SellFinishBinding = binding

        binding.list.addItemDecoration(DividerItemDecoration(binding.list.context, DividerItemDecoration.VERTICAL))
        binding.list.layoutManager = LinearLayoutManager(context)
        binding.list.adapter = adapterListSell

        ///////////////////////////////////////////////////////////////////
        binding.start1000Button.setOnClickListener {
            if (Utils.isServiceRunning(requireContext(), Strategy1000SellService::class.java)) {
                requireContext().stopService(Intent(context, Strategy1000SellService::class.java))
            } else {
                if (strategy1000Sell.getTotalPurchasePieces() > 0) {
                    Utils.startService(requireContext(), Strategy1000SellService::class.java)
                }
            }

            this.findNavController().navigateUp()
            updateServiceButtonText1000()
        }
        updateServiceButtonText1000()
        //////////////////////////////////////////////////////////////////
        binding.start700Button.setOnClickListener {
            if (Utils.isServiceRunning(requireContext(), Strategy700SellService::class.java)) {
                requireContext().stopService(Intent(context, Strategy700SellService::class.java))
            } else {
                if (strategy1000Sell.getTotalPurchasePieces() > 0) {
                    Utils.startService(requireContext(), Strategy700SellService::class.java)
                }
            }

            this.findNavController().navigateUp()
            updateServiceButtonText700()
        }
        updateServiceButtonText700()

        positions = strategy1000Sell.processSellPosition()
        adapterListSell.setData(positions)
        updateInfoText()
    }

    private fun updateServiceButtonText1000() {
        if (Utils.isServiceRunning(requireContext(), Strategy1000SellService::class.java)) {
            fragment1000SellFinishBinding?.start1000Button?.text = getString(R.string.stop_sell_1000)
        } else {
            fragment1000SellFinishBinding?.start1000Button?.text = getString(R.string.start_sell_1000)
        }
    }

    private fun updateServiceButtonText700() {
        if (Utils.isServiceRunning(requireContext(), Strategy700SellService::class.java)) {
            fragment1000SellFinishBinding?.start700Button?.text = getString(R.string.stop_sell_700)
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

    inner class Item1000SellRecyclerViewAdapter(private var values: List<PurchaseStock>) : RecyclerView.Adapter<Item1000SellRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<PurchaseStock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(Fragment1000SellFinishItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: Fragment1000SellFinishItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val purchaseStock = values[index]

                with(binding) {
                    val avg = purchaseStock.position.getAveragePrice()
                    tickerView.text = "${purchaseStock.position.ticker} x ${purchaseStock.position.lots}"

                    val profit = purchaseStock.position.getProfitAmount()
                    var totalCash = purchaseStock.position.balance * avg
                    val percent = purchaseStock.position.getProfitPercent()
                    percentProfitView.text = percent.toPercent()

                    totalCash += profit
                    priceView.text = "${avg.toMoney(purchaseStock.stock)} ➡ ${totalCash.toMoney(purchaseStock.stock)}"

                    priceProfitTotalView.text = profit.toMoney(purchaseStock.stock)
                    priceProfitView.text = (profit / purchaseStock.position.lots).toMoney(purchaseStock.stock)

                    priceView.setTextColor(Utils.getColorForValue(percent))
                    percentProfitView.setTextColor(Utils.getColorForValue(percent))
                    priceProfitView.setTextColor(Utils.getColorForValue(percent))
                    priceProfitTotalView.setTextColor(Utils.getColorForValue(percent))

                    refreshPercent(purchaseStock)

                    percentPlusButton.setOnClickListener {
                        purchaseStock.percentProfitSellFrom += 0.05
                        refreshPercent(purchaseStock)
                        updateInfoText()
                    }

                    percentMinusButton.setOnClickListener {
                        purchaseStock.percentProfitSellFrom += -0.05
                        refreshPercent(purchaseStock)
                        updateInfoText()
                    }

                    itemView.setBackgroundColor(Utils.getColorForIndex(index))
                }
            }

            fun refreshPercent(item: PurchaseStock) {
                with(binding) {
                    val futurePercent = item.percentProfitSellFrom
                    percentProfitFutureView.text = futurePercent.toPercent()

                    val avg = item.position.getAveragePrice()
                    val futureProfitPrice = item.getProfitPriceForSell() - avg
                    priceProfitFutureView.text = futureProfitPrice.toMoney(item.stock)
                    percentProfitFutureTotalView.text = (futureProfitPrice * item.position.balance).toMoney(item.stock)

                    val sellPrice = item.getProfitPriceForSell()
                    val totalSellPrice = item.getProfitPriceForSell() * item.position.balance
                    priceTotalView.text = "${sellPrice.toMoney(item.stock)} ➡ ${totalSellPrice.toMoney(item.stock)}"

                    priceTotalView.setTextColor(Utils.getColorForValue(futureProfitPrice))
                    percentProfitFutureView.setTextColor(Utils.getColorForValue(futureProfitPrice))
                    priceProfitFutureView.setTextColor(Utils.getColorForValue(futureProfitPrice))
                    percentProfitFutureTotalView.setTextColor(Utils.getColorForValue(futureProfitPrice))
                }
            }
        }
    }
}