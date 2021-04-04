package com.project.ti2358.ui.favorites

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
import com.project.ti2358.data.manager.ChartManager
import com.project.ti2358.data.manager.OrderbookManager
import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.manager.StrategyFavorites
import com.project.ti2358.databinding.FragmentFavoritesBinding
import com.project.ti2358.databinding.FragmentFavoritesItemBinding
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class FavoritesFragment : Fragment(R.layout.fragment_favorites) {
    val orderbookManager: OrderbookManager by inject()
    val chartManager: ChartManager by inject()
    val strategyFavorites: StrategyFavorites by inject()

    private var fragmentFavoritesBinding: FragmentFavoritesBinding? = null

    var adapterList: ItemFavoritesRecyclerViewAdapter = ItemFavoritesRecyclerViewAdapter(emptyList())
    lateinit var stocks: MutableList<Stock>

    override fun onDestroy() {
        fragmentFavoritesBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentFavoritesBinding.bind(view)
        fragmentFavoritesBinding = binding

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
        stocks = strategyFavorites.process()
        stocks = strategyFavorites.resort()
        stocks = Utils.search(stocks, query)
        adapterList.setData(stocks)
    }

    private fun updateTitle() {
        val act = requireActivity() as AppCompatActivity
        act.supportActionBar?.title = "Избранные (${StrategyFavorites.stocksSelected.size} шт.)"
    }

    inner class ItemFavoritesRecyclerViewAdapter(private var values: List<Stock>) : RecyclerView.Adapter<ItemFavoritesRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<Stock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FragmentFavoritesItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentFavoritesItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val stock = values[index]
                with(binding) {
                    chooseView.setOnCheckedChangeListener(null)
                    chooseView.isChecked = strategyFavorites.isSelected(stock)

                    tickerView.text = "${index + 1}) ${stock.getTickerLove()}"
                    priceView.text = "${stock.getPrice2359String()} ➡ ${stock.getPriceString()}"

                    priceChangeAbsoluteView.text = stock.changePrice2300DayAbsolute.toMoney(stock)
                    priceChangePercentView.text = stock.changePrice2300DayPercent.toPercent()

                    priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(stock.changePrice2300DayAbsolute))
                    priceChangePercentView.setTextColor(Utils.getColorForValue(stock.changePrice2300DayAbsolute))

                    chooseView.setOnCheckedChangeListener { _, checked ->
                        strategyFavorites.setSelected(stock, checked)
                        updateTitle()
                    }

                    itemView.setOnClickListener {
                        Utils.openTinkoffForTicker(requireContext(), stock.ticker)
                    }

                    orderbookButton.setOnClickListener {
                        orderbookManager.start(stock)
                        orderbookButton.findNavController().navigate(R.id.action_nav_favorites_to_nav_orderbook)
                    }

                    chartButton.setOnClickListener {
                        chartManager.start(stock)
                        chartButton.findNavController().navigate(R.id.action_nav_favorites_to_nav_chart)
                    }

                    itemView.setBackgroundColor(Utils.getColorForIndex(index))
                }
            }
        }
    }
}