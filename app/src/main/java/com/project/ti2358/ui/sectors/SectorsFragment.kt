package com.project.ti2358.ui.sectors

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.*
import com.project.ti2358.databinding.FragmentPremarketItemBinding
import com.project.ti2358.databinding.FragmentSectorBinding
import com.project.ti2358.service.Utils
import com.project.ti2358.service.toMoney
import com.project.ti2358.service.toPercent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.util.*

@KoinApiExtension
class SectorsFragment : Fragment(R.layout.fragment_sector) {
    val stockManager: StockManager by inject()
    val strategySector: StrategySector by inject()
    val orderbookManager: OrderbookManager by inject()

    private var fragmentSectorBinding: FragmentSectorBinding? = null

    var adapterList: ItemPremarketRecyclerViewAdapter = ItemPremarketRecyclerViewAdapter(emptyList())
    var stocks: MutableList<Stock> = mutableListOf()
    var sectors: MutableList<String> = mutableListOf()
    var currentSector: String = ""
    var currentSectorIndex: Int = -1
    var job: Job? = null

    override fun onDestroy() {
        job?.cancel()
        fragmentSectorBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentSectorBinding.bind(view)
        fragmentSectorBinding = binding

        with(binding) {
            list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapterList

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
                    updateData(search = text)
                }
            })

            searchView.setOnCloseListener {
                updateData(search = searchView.query.toString())
                false
            }

            sectors = stockManager.stockSectors
            currentSector = sectors.first()

            sector1Button.setOnClickListener {
                updateData(0, searchView.query.toString())
            }
            sector2Button.setOnClickListener {
                updateData(1, searchView.query.toString())
            }
            sector3Button.setOnClickListener {
                updateData(2, searchView.query.toString())
            }
            sector4Button.setOnClickListener {
                updateData(3, searchView.query.toString())
            }
            sector5Button.setOnClickListener {
                updateData(4, searchView.query.toString())
            }
            sector6Button.setOnClickListener {
                updateData(5, searchView.query.toString())
            }
            sector7Button.setOnClickListener {
                updateData(6, searchView.query.toString())
            }
            sector8Button.setOnClickListener {
                updateData(7, searchView.query.toString())
            }
            sector9Button.setOnClickListener {
                updateData(8, searchView.query.toString())
            }
            sector10Button.setOnClickListener {
                updateData(9, searchView.query.toString())
            }
            sector11Button.setOnClickListener {
                updateData(10, searchView.query.toString())
            }
        }

        job?.cancel()
        job = GlobalScope.launch(Dispatchers.Main) {
            stockManager.reloadClosePrices()
            updateData()
        }

        updateData(0)
    }

    private fun updateData(sectorIndex: Int = -1, search: String = "") {
        if (sectorIndex != -1) {
            currentSectorIndex = sectorIndex
            currentSector = sectors[sectorIndex]
        }
        stocks = strategySector.process()
        stocks = strategySector.resort(currentSector).toMutableList()
        if (search != "") stocks = Utils.search(stocks, search)

        adapterList.setData(stocks)
        updateButtons()
        updateTitle()
    }

    private fun updateTitle() {
        if (isAdded) {
            val act = requireActivity() as AppCompatActivity
            act.supportActionBar?.title = "$currentSector ${stocks.size}"
        }
    }

    private fun updateButtons() {
        fragmentSectorBinding?.apply {
            val colorDefault = Utils.DARK_BLUE
            val colorSelect = Utils.RED

            sector1Button.setBackgroundColor(colorDefault)
            sector2Button.setBackgroundColor(colorDefault)
            sector3Button.setBackgroundColor(colorDefault)
            sector4Button.setBackgroundColor(colorDefault)
            sector5Button.setBackgroundColor(colorDefault)
            sector6Button.setBackgroundColor(colorDefault)
            sector7Button.setBackgroundColor(colorDefault)
            sector8Button.setBackgroundColor(colorDefault)
            sector9Button.setBackgroundColor(colorDefault)
            sector10Button.setBackgroundColor(colorDefault)
            sector11Button.setBackgroundColor(colorDefault)

            when (currentSectorIndex) {
                0 -> sector1Button.setBackgroundColor(colorSelect)
                1 -> sector2Button.setBackgroundColor(colorSelect)
                2 -> sector3Button.setBackgroundColor(colorSelect)
                3 -> sector4Button.setBackgroundColor(colorSelect)
                4 -> sector5Button.setBackgroundColor(colorSelect)
                5 -> sector6Button.setBackgroundColor(colorSelect)
                6 -> sector7Button.setBackgroundColor(colorSelect)
                7 -> sector8Button.setBackgroundColor(colorSelect)
                8 -> sector9Button.setBackgroundColor(colorSelect)
                9 -> sector10Button.setBackgroundColor(colorSelect)
                10 -> sector11Button.setBackgroundColor(colorSelect)
            }
        }
    }

    inner class ItemPremarketRecyclerViewAdapter(private var values: List<Stock>) : RecyclerView.Adapter<ItemPremarketRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<Stock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FragmentPremarketItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentPremarketItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val stock = values[index]
                with(binding) {
                    tickerView.text = "${index + 1}) ${stock.getTickerLove()}"

                    val volume = stock.getTodayVolume() / 1000f
                    volumeSharesView.text = "%.1fk".format(locale = Locale.US, volume)

                    val volumeCash = stock.dayVolumeCash / 1000f / 1000f
                    volumeCashView.text = "%.2fM$".format(locale = Locale.US, volumeCash)

                    priceView.text = "${stock.getPrice2359String()} ➡ ${stock.getPriceString()}"

                    priceChangeAbsoluteView.text = stock.changePrice2300DayAbsolute.toMoney(stock)
                    priceChangePercentView.text = stock.changePrice2300DayPercent.toPercent()

                    priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(stock.changePrice2300DayAbsolute))
                    priceChangePercentView.setTextColor(Utils.getColorForValue(stock.changePrice2300DayAbsolute))
                    priceView.setTextColor(Utils.getColorForValue(stock.changePrice2300DayAbsolute))

                    itemView.setOnClickListener {
                        Utils.openTinkoffForTicker(requireContext(), stock.ticker)
                    }

                    orderbookButton.setOnClickListener {
                        orderbookManager.start(stock)
                        orderbookButton.findNavController().navigate(R.id.action_nav_sectors_to_nav_orderbook)
                    }

                    sectorView.text = stock.getSectorName()
                    sectorView.setTextColor(Utils.getColorForSector(stock.closePrices?.sector))

                    itemView.setOnClickListener {
                        Utils.openTinkoffForTicker(requireContext(), stock.ticker)
                    }

                    if (stock.report != null) {
                        reportInfoView.text = stock.getReportInfo()
                        reportInfoView.visibility = View.VISIBLE
                    } else {
                        reportInfoView.visibility = View.GONE
                    }
                    reportInfoView.setTextColor(Utils.RED)
                    itemView.setBackgroundColor(Utils.getColorForIndex(index))
                }
            }
        }
    }
}