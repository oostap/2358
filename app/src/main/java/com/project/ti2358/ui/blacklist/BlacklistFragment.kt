package com.project.ti2358.ui.blacklist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.manager.StrategyBlacklist
import com.project.ti2358.databinding.FragmentBlacklistBinding
import com.project.ti2358.databinding.FragmentBlacklistItemBinding
import com.project.ti2358.databinding.FragmentFavoritesItemBinding
import com.project.ti2358.service.*
import com.project.ti2358.ui.favorites.FavoritesFragment
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class BlacklistFragment : Fragment(R.layout.fragment_blacklist) {
    val strategyBlacklist: StrategyBlacklist by inject()

    private var fragmentBlacklistBinding: FragmentBlacklistBinding? = null

    var adapterList: ItemBlacklistRecyclerViewAdapter = ItemBlacklistRecyclerViewAdapter(emptyList())
    lateinit var stocks: MutableList<Stock>

    override fun onDestroy() {
        fragmentBlacklistBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentBlacklistBinding.bind(view)
        fragmentBlacklistBinding = binding

        binding.list.addItemDecoration(DividerItemDecoration(binding.list.context, DividerItemDecoration.VERTICAL))
        binding.list.layoutManager = LinearLayoutManager(context)
        binding.list.adapter = adapterList

        binding.buttonUpdate.setOnClickListener {
            updateData()
        }

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                processText(query)
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                processText(newText)
                return false
            }

            fun processText(text: String) {
                updateData()

                stocks = Utils.search(stocks, text)
                adapterList.setData(stocks)
            }
        })

        binding.searchView.setOnCloseListener {
            updateData()
            false
        }

        updateData()
    }

    private fun updateData() {
        stocks = strategyBlacklist.process()
        stocks = strategyBlacklist.resort()
        adapterList.setData(stocks)

        updateTitle()
    }

    private fun updateTitle() {
        val act = requireActivity() as AppCompatActivity
        act.supportActionBar?.title = "Чёрный список (${strategyBlacklist.stocksSelected.size} шт.)"
    }

    inner class ItemBlacklistRecyclerViewAdapter(private var values: List<Stock>) : RecyclerView.Adapter<ItemBlacklistRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<Stock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(FragmentBlacklistItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)

        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentBlacklistItemBinding) : RecyclerView.ViewHolder(binding.root) {
            lateinit var stock: Stock

            fun bind(index: Int) {
                val item = values[index]
                stock = item

                binding.chooseView.setOnCheckedChangeListener(null)
                binding.chooseView.isChecked = strategyBlacklist.isSelected(item)

                binding.tickerView.text = "${index + 1}) ${item.getTickerLove()}"
                binding.priceView.text = "${item.getPrice2359String()} ➡ ${item.getPriceString()}"

                val volume = item.getTodayVolume() / 1000f
                binding.volumeSharesView.text = "%.1fk".format(volume)

                val volumeCash = item.dayVolumeCash / 1000f / 1000f
                binding.volumeCashView.text = "%.2fM$".format(volumeCash)

                binding.priceChangeAbsoluteView.text = item.changePrice2359DayAbsolute.toMoney(item)
                binding.priceChangePercentView.text = item.changePrice2359DayPercent.toPercent()

                binding.priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(item.changePrice2359DayAbsolute))
                binding.priceChangePercentView.setTextColor(Utils.getColorForValue(item.changePrice2359DayAbsolute))

                binding.chooseView.setOnCheckedChangeListener { _, checked ->
                    strategyBlacklist.setSelected(stock, checked)
                    updateTitle()
                }

                itemView.setOnClickListener {
                    Utils.openTinkoffForTicker(requireContext(), stock.ticker)
                }

                itemView.setBackgroundColor(Utils.getColorForIndex(index))
            }
        }
    }
}