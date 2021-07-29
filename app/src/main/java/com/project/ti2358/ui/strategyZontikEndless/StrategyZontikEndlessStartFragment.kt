package com.project.ti2358.ui.strategyZontikEndless

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
import com.project.ti2358.data.manager.OrderbookManager
import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.manager.StockManager
import com.project.ti2358.data.manager.StrategyZontikEndless
import com.project.ti2358.databinding.FragmentZontikEndlessStartBinding
import com.project.ti2358.databinding.FragmentZontikEndlessStartItemBinding
import com.project.ti2358.service.*
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class StrategyZontikEndlessStartFragment : Fragment(R.layout.fragment_zontik_endless_start) {
    val strategyZontikEndless: StrategyZontikEndless by inject()
    private val orderbookManager: OrderbookManager by inject()

    private var fragmentZontikEndlessStartBinding: FragmentZontikEndlessStartBinding? = null

    var adapterList: ItemTazikRecyclerViewAdapter = ItemTazikRecyclerViewAdapter(emptyList())
    lateinit var stocks: MutableList<Stock>
    var numberSet: Int = 1

    override fun onDestroy() {
        fragmentZontikEndlessStartBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentZontikEndlessStartBinding.bind(view)
        fragmentZontikEndlessStartBinding = binding

        with(binding) {
            list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapterList

            startButton.setOnClickListener {
                if (strategyZontikEndless.stocksSelected.isNotEmpty()) {
                    view.findNavController().navigate(R.id.action_nav_zontik_endless_start_to_nav_zontik_endless_finish)
                } else {
                    Utils.showErrorAlert(requireContext())
                }
            }

            set1Button.setOnClickListener {
                numberSet = 1
                updateData()
            }

            set2Button.setOnClickListener {
                numberSet = 2
                updateData()
            }

            set3Button.setOnClickListener {
                numberSet = 3
                updateData()
            }

            setLoveButton.setOnClickListener {
                numberSet = 4
                updateData()
            }

            updateButton.setOnClickListener {
                updateData()
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
                    updateData(text)
                }
            })

            searchView.setOnCloseListener {
                updateData()
                false
            }
        }

        updateData()
    }

    private fun updateData(search: String = "") {
        GlobalScope.launch(StockManager.stockContext) {
            strategyZontikEndless.process(numberSet)
            stocks = strategyZontikEndless.resort()
            if (search != "") stocks = Utils.search(stocks, search)

            withContext(Dispatchers.Main) {
                adapterList.setData(stocks)
                updateTitle()

                fragmentZontikEndlessStartBinding?.let {
                    val colorDefault = Utils.DARK_BLUE
                    val colorSelect = Utils.RED

                    it.set1Button.setBackgroundColor(colorDefault)
                    it.set2Button.setBackgroundColor(colorDefault)
                    it.set3Button.setBackgroundColor(colorDefault)
                    it.setLoveButton.setBackgroundColor(colorDefault)

                    when (numberSet) {
                        1 -> it.set1Button.setBackgroundColor(colorSelect)
                        2 -> it.set2Button.setBackgroundColor(colorSelect)
                        3 -> it.set3Button.setBackgroundColor(colorSelect)
                        4 -> it.setLoveButton.setBackgroundColor(colorSelect)
                        else -> {
                        }
                    }
                }
            }
        }
    }

    private fun updateTitle() {
        if (isAdded) {
            val act = requireActivity() as AppCompatActivity
            act.supportActionBar?.title = "Бесконечный ☂️ (${strategyZontikEndless.stocksSelected.size} шт.)"
        }
    }

    inner class ItemTazikRecyclerViewAdapter(private var values: List<Stock>) : RecyclerView.Adapter<ItemTazikRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<Stock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FragmentZontikEndlessStartItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentZontikEndlessStartItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val stock = values[index]

                with(binding) {
                    chooseView.setOnCheckedChangeListener(null)
                    chooseView.isChecked = strategyZontikEndless.isSelected(stock)

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

                    sectorView.text = stock.getSectorName()
                    sectorView.setTextColor(Utils.getColorForSector(stock.closePrices?.sector))

                    chooseView.setOnCheckedChangeListener { _, checked ->
                        GlobalScope.launch {
                            strategyZontikEndless.setSelected(stock, checked, numberSet)
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
}