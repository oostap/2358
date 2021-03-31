package com.project.ti2358.ui.strategyTazikEndless

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
import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.manager.StrategyTazikEndless
import com.project.ti2358.databinding.FragmentTazikEndlessStartBinding
import com.project.ti2358.databinding.FragmentTazikEndlessStartItemBinding
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class StrategyTazikEndlessStartFragment : Fragment(R.layout.fragment_tazik_endless_start) {
    val strategyTazikEndless: StrategyTazikEndless by inject()

    private var fragmentTazikEndlessStartBinding: FragmentTazikEndlessStartBinding? = null

    var adapterList: ItemTazikRecyclerViewAdapter = ItemTazikRecyclerViewAdapter(emptyList())
    lateinit var stocks: MutableList<Stock>

    override fun onDestroy() {
        fragmentTazikEndlessStartBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentTazikEndlessStartBinding.bind(view)
        fragmentTazikEndlessStartBinding = binding

        binding.list.addItemDecoration(DividerItemDecoration(binding.list.context, DividerItemDecoration.VERTICAL))
        binding.list.layoutManager = LinearLayoutManager(context)
        binding.list.adapter = adapterList

        binding.startButton.setOnClickListener {
            if (strategyTazikEndless.stocksSelected.isNotEmpty()) {
                view.findNavController().navigate(R.id.action_nav_tazik_start_to_nav_tazik_finish)
            } else {
                Utils.showErrorAlert(requireContext())
            }
        }

        binding.updateButton.setOnClickListener {
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
        stocks = strategyTazikEndless.process()
        stocks = strategyTazikEndless.resort()
        adapterList.setData(stocks)

        updateTitle()
    }

    private fun updateTitle() {
        val act = requireActivity() as AppCompatActivity
        act.supportActionBar?.title = "Бесконечный таз (${strategyTazikEndless.stocksSelected.size} шт.)"
    }

    inner class ItemTazikRecyclerViewAdapter(private var values: List<Stock>) : RecyclerView.Adapter<ItemTazikRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<Stock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FragmentTazikEndlessStartItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentTazikEndlessStartItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val stock = values[index]

                with(binding) {
                    chooseView.setOnCheckedChangeListener(null)
                    chooseView.isChecked = strategyTazikEndless.isSelected(stock)

                    tickerView.text = "${index + 1}) ${stock.getTickerLove()}"
                    priceView.text = "${stock.getPrice2359String()} ➡ ${stock.getPriceString()}"

                    val volume = stock.getTodayVolume() / 1000f
                    volumeSharesView.text = "%.1fk".format(volume)

                    val volumeCash = stock.dayVolumeCash / 1000f / 1000f
                    volumeCashView.text = "%.2fM$".format(volumeCash)

                    priceChangeAbsoluteView.text = stock.changePrice2300DayAbsolute.toMoney(stock)
                    priceChangePercentView.text = stock.changePrice2300DayPercent.toPercent()

                    priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(stock.changePrice2300DayAbsolute))
                    priceChangePercentView.setTextColor(Utils.getColorForValue(stock.changePrice2300DayAbsolute))

                    chooseView.setOnCheckedChangeListener { _, checked ->
                        strategyTazikEndless.setSelected(stock, checked)
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
}