package com.project.ti2358.ui.love

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation.findNavController
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.*
import com.project.ti2358.databinding.FragmentLoveBinding
import com.project.ti2358.databinding.FragmentLoveItemBinding
import com.project.ti2358.service.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class LoveFragment : Fragment(R.layout.fragment_love) {
    val orderbookManager: OrderbookManager by inject()
    val chartManager: ChartManager by inject()
    val strategyLove: StrategyLove by inject()
    val stockManager: StockManager by inject()

    private var fragmentLoveBinding: FragmentLoveBinding? = null

    var adapterList: ItemFavoritesRecyclerViewAdapter = ItemFavoritesRecyclerViewAdapter(emptyList())
    lateinit var stocks: MutableList<Stock>

    override fun onDestroy() {
        fragmentLoveBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentLoveBinding.bind(view)
        fragmentLoveBinding = binding

        with(binding) {
            list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapterList

            binding.updateButton.setOnClickListener {
                updateData()
            }

            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    updateData(query)
                    return false
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    updateData(newText)
                    return false
                }
            })

            searchView.setOnCloseListener {
                updateData()
                false
            }

            updateData(searchView.query.toString())
        }
        updateTitle()
    }

    private fun updateData(query: String = "") {
        GlobalScope.launch(Dispatchers.Main) {
            stocks = strategyLove.process(stockManager.stocksStream)
            stocks = strategyLove.resort()
            if (query != "") stocks = Utils.search(stocks, query)
            adapterList.setData(stocks)
            updateTitle()
        }
    }

    private fun updateTitle() {
        if (isAdded) {
            val act = requireActivity() as AppCompatActivity
            act.supportActionBar?.title = "Избранные (${StrategyLove.stocksSelected.size} шт.)"
        }
    }

    inner class ItemFavoritesRecyclerViewAdapter(private var values: List<Stock>) : RecyclerView.Adapter<ItemFavoritesRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<Stock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FragmentLoveItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentLoveItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val stock = values[index]
                with(binding) {
                    chooseView.setOnCheckedChangeListener(null)
                    chooseView.isChecked = strategyLove.isSelected(stock)

                    tickerView.text = "${index + 1}) ${stock.getTickerLove()}"
                    priceView.text = "${stock.getPrice2359String()} ➡ ${stock.getPriceString()}"

                    priceChangeAbsoluteView.text = stock.changePrice2300DayAbsolute.toMoney(stock)
                    priceChangePercentView.text = stock.changePrice2300DayPercent.toPercent()

                    priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(stock.changePrice2300DayAbsolute))
                    priceChangePercentView.setTextColor(Utils.getColorForValue(stock.changePrice2300DayAbsolute))

                    chooseView.setOnCheckedChangeListener { _, checked ->
                        strategyLove.setSelected(stock, checked)
                        updateTitle()
                    }

                    orderbookButton.setOnClickListener {
                        Utils.openOrderbookForStock(it.findNavController(), orderbookManager, stock)
                    }

                    chartButton.setOnClickListener {
                        Utils.openChartForStock(it.findNavController(), chartManager, stock)
                    }

                    itemView.setBackgroundColor(Utils.getColorForIndex(index))
                }
            }
        }
    }
}