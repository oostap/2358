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
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class FavoritesFragment : Fragment() {
    val orderbookManager: OrderbookManager by inject()
    val chartManager: ChartManager by inject()
    val strategyFavorites: StrategyFavorites by inject()
    var adapterList: ItemBlacklistRecyclerViewAdapter = ItemBlacklistRecyclerViewAdapter(emptyList())
    lateinit var searchView: SearchView
    lateinit var stocks: MutableList<Stock>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_favorites, container, false)
        val list = view.findViewById<RecyclerView>(R.id.list)

        list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))

        if (list is RecyclerView) {
            with(list) {
                layoutManager = LinearLayoutManager(context)
                adapter = adapterList
            }
        }

        val buttonUpdate = view.findViewById<Button>(R.id.button_update)
        buttonUpdate.setOnClickListener {
            updateData()
        }

        searchView = view.findViewById(R.id.searchView)
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
                updateData()

                stocks = Utils.search(stocks, text)
                adapterList.setData(stocks)
            }
        })

        searchView.setOnCloseListener {
            updateData()
            false
        }

        updateData()
        return view
    }

    private fun updateData() {
        stocks = strategyFavorites.process()
        stocks = strategyFavorites.resort()
        adapterList.setData(stocks)

        updateTitle()
    }

    private fun updateTitle() {
        val act = requireActivity() as AppCompatActivity
        act.supportActionBar?.title = "Избранные (${StrategyFavorites.stocksSelected.size} шт.)"
    }

    inner class ItemBlacklistRecyclerViewAdapter(
        private var values: List<Stock>
    ) : RecyclerView.Adapter<ItemBlacklistRecyclerViewAdapter.ViewHolder>() {

        fun setData(newValues: List<Stock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.fragment_favorites_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.stock = item

            holder.checkBoxView.setOnCheckedChangeListener(null)
            holder.checkBoxView.isChecked = strategyFavorites.isSelected(item)

            holder.tickerView.text = "${position + 1}) ${item.getTickerLove()}"
            holder.priceView.text = "${item.getPrice2359String()} ➡ ${item.getPriceString()}"

            holder.changePriceAbsoluteView.text = item.changePrice2359DayAbsolute.toMoney(item)
            holder.changePricePercentView.text = item.changePrice2359DayPercent.toPercent()

            holder.changePriceAbsoluteView.setTextColor(Utils.getColorForValue(item.changePrice2359DayAbsolute))
            holder.changePricePercentView.setTextColor(Utils.getColorForValue(item.changePrice2359DayAbsolute))

            holder.checkBoxView.setOnCheckedChangeListener { _, checked ->
                strategyFavorites.setSelected(holder.stock, checked)
                updateTitle()
            }

            holder.itemView.setOnClickListener {
                Utils.openTinkoffForTicker(requireContext(), holder.stock.ticker)
            }

            holder.orderbookImageView.setOnClickListener {
                orderbookManager.start(holder.stock)
                holder.orderbookImageView.findNavController().navigate(R.id.action_nav_favorites_to_nav_orderbook)
            }

            holder.chartImageView.setOnClickListener {
                chartManager.start(holder.stock)
                holder.chartImageView.findNavController().navigate(R.id.action_nav_favorites_to_nav_chart)
            }

            holder.itemView.setBackgroundColor(Utils.getColorForIndex(position))
        }

        override fun getItemCount(): Int = values.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            lateinit var stock: Stock

            val tickerView: TextView = view.findViewById(R.id.stock_item_ticker)
            val priceView: TextView = view.findViewById(R.id.stock_item_price)

            val changePriceAbsoluteView: TextView = view.findViewById(R.id.stock_item_price_change_absolute)
            val changePricePercentView: TextView = view.findViewById(R.id.stock_item_price_change_percent)

            val checkBoxView: CheckBox = view.findViewById(R.id.check_box)

            val orderbookImageView: ImageView = view.findViewById(R.id.orderbook)
            val chartImageView: ImageView = view.findViewById(R.id.chart)
        }
    }
}