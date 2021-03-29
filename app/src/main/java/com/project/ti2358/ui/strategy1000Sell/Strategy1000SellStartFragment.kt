package com.project.ti2358.ui.strategy1000Sell

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.DepositManager
import com.project.ti2358.data.manager.Strategy1000Sell
import com.project.ti2358.data.model.dto.PortfolioPosition
import com.project.ti2358.databinding.Fragment1000SellStartBinding
import com.project.ti2358.databinding.Fragment1000SellStartItemBinding
import com.project.ti2358.service.Utils
import com.project.ti2358.service.toMoney
import com.project.ti2358.service.toPercent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class Strategy1000SellStartFragment : Fragment(R.layout.fragment_1000_sell_start) {
    val strategy1000Sell: Strategy1000Sell by inject()
    val depositManager: DepositManager by inject()

    private var fragment1000SellStartBinding: Fragment1000SellStartBinding? = null

    var adapterList: Item1000SellRecyclerViewAdapter = Item1000SellRecyclerViewAdapter(emptyList())
    var jobUpdate: Job? = null

    override fun onDestroy() {
        jobUpdate?.cancel()
        fragment1000SellStartBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = Fragment1000SellStartBinding.bind(view)
        fragment1000SellStartBinding = binding

        binding.list.addItemDecoration(DividerItemDecoration(binding.list.context, DividerItemDecoration.VERTICAL))
        binding.list.layoutManager = LinearLayoutManager(context)
        binding.list.adapter = adapterList

        binding.startButton.setOnClickListener {
            if (strategy1000Sell.positionsSelected.isNotEmpty()) {
                view.findNavController().navigate(R.id.action_nav_1000_sell_start_to_nav_1000_sell_finish)
            } else {
                Utils.showErrorAlert(requireContext())
            }
        }

        binding.updateButton.setOnClickListener {
            updateData()
        }

        jobUpdate?.cancel()
        jobUpdate = GlobalScope.launch(Dispatchers.Main) {
            depositManager.refreshDeposit()
            updateData()
        }
        updateData()
    }

    fun updateData() {
        adapterList.setData(depositManager.portfolioPositions)
    }

    inner class Item1000SellRecyclerViewAdapter(private var values: List<PortfolioPosition>) : RecyclerView.Adapter<Item1000SellRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<PortfolioPosition>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(Fragment1000SellStartItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: Fragment1000SellStartItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val portfolioPosition = values[index]

                with(binding) {
                    tickerView.text = "${index + 1}) ${portfolioPosition.ticker}"

                    chooseView.setOnCheckedChangeListener(null)
                    chooseView.isChecked = strategy1000Sell.isSelected(portfolioPosition)

                    val avg = portfolioPosition.getAveragePrice()
                    volumeSharesView.text = "${portfolioPosition.lots} шт."
                    priceView.text = avg.toMoney(portfolioPosition.stock)

                    val profit = portfolioPosition.getProfitAmount()
                    priceChangeAbsoluteView.text = profit.toMoney(portfolioPosition.stock)

                    var totalCash = portfolioPosition.balance * avg
                    val percent = portfolioPosition.getProfitPercent()
                    priceChangePercentView.text = percent.toPercent()

                    totalCash += profit
                    volumeCashView.text = totalCash.toMoney(portfolioPosition.stock)

                    priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(profit))
                    priceChangePercentView.setTextColor(Utils.getColorForValue(profit))

                    chooseView.setOnCheckedChangeListener { _, checked ->
                        strategy1000Sell.setSelected(portfolioPosition, checked)
                    }

                    itemView.setOnClickListener { _ ->
                        portfolioPosition.stock?.let {
                            Utils.openTinkoffForTicker(requireContext(), it.ticker)
                        }
                    }

                    itemView.setBackgroundColor(Utils.getColorForIndex(index))
                }
            }
        }
    }
}