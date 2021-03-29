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
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class StrategyTazikEndlessStartFragment : Fragment() {

    val strategyTazikEndless: StrategyTazikEndless by inject()
    var adapterList: ItemTazikRecyclerViewAdapter = ItemTazikRecyclerViewAdapter(emptyList())
    lateinit var searchView: SearchView
    lateinit var stocks: MutableList<Stock>

    var numberSet: Int = 1
    var sort = Sorting.DESCENDING

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tazik_start, container, false)
        val list = view.findViewById<RecyclerView>(R.id.list)

        list.addItemDecoration(
            DividerItemDecoration(
                list.context,
                DividerItemDecoration.VERTICAL
            )
        )

        if (list is RecyclerView) {
            with(list) {
                layoutManager = LinearLayoutManager(context)
                adapter = adapterList
            }
        }

        val buttonStart = view.findViewById<Button>(R.id.button_start)
        buttonStart.setOnClickListener {
            if (strategyTazikEndless.stocksSelected.isNotEmpty()) {
                view.findNavController().navigate(R.id.action_nav_tazik_start_to_nav_tazik_finish)
            } else {
                Utils.showErrorAlert(requireContext())
            }
        }

        val buttonUpdate = view.findViewById<Button>(R.id.`@+id/update_button`)
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

        val buttonSet1 = view.findViewById<Button>(R.id.buttonSet1)
        buttonSet1.setOnClickListener {
            numberSet = 1
            updateData()
        }

        val buttonSet2 = view.findViewById<Button>(R.id.buttonSet2)
        buttonSet2.setOnClickListener {
            numberSet = 2
            updateData()
        }

        updateData()
        return view
    }

    private fun updateData() {
        stocks = strategyTazikEndless.process(numberSet)
        stocks = strategyTazikEndless.resort()
        adapterList.setData(stocks)

        updateTitle()
    }

    private fun updateTitle() {
        val act = requireActivity() as AppCompatActivity
        act.supportActionBar?.title = "Автотазик - Набор $numberSet (${strategyTazikEndless.stocksSelected.size} шт.)"
    }

    inner class ItemTazikRecyclerViewAdapter(
        private var values: List<Stock>
    ) : RecyclerView.Adapter<ItemTazikRecyclerViewAdapter.ViewHolder>() {

        fun setData(newValues: List<Stock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(
                R.layout.fragment_tazik_start_item,
                parent,
                false
            )

            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.stock = item

            holder.checkBoxView.setOnCheckedChangeListener(null)
            holder.checkBoxView.isChecked = strategyTazikEndless.isSelected(item)

            holder.tickerView.text = "${position + 1}) ${item.getTickerLove()}"
            holder.priceView.text = "${item.getPrice2359String()} ➡ ${item.getPriceString()}"

            val volume = item.getTodayVolume() / 1000f
            holder.volumeTodayView.text = "%.1fk".format(volume)

            val volumeCash = item.dayVolumeCash / 1000f / 1000f
            holder.volumeTodayCashView.text = "%.2fM$".format(volumeCash)

            holder.changePriceAbsoluteView.text = item.changePrice2359DayAbsolute.toMoney(item)
            holder.changePricePercentView.text = item.changePrice2359DayPercent.toPercent()

            holder.changePriceAbsoluteView.setTextColor(Utils.getColorForValue(item.changePrice2359DayAbsolute))
            holder.changePricePercentView.setTextColor(Utils.getColorForValue(item.changePrice2359DayAbsolute))

            holder.checkBoxView.setOnCheckedChangeListener { _, checked ->
                strategyTazikEndless.setSelected(holder.stock, checked, numberSet)
                updateTitle()
            }

            holder.itemView.setOnClickListener {
                Utils.openTinkoffForTicker(requireContext(), holder.stock.ticker)
            }

            holder.itemView.setBackgroundColor(Utils.getColorForIndex(position))
        }

        override fun getItemCount(): Int = values.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            lateinit var stock: Stock

            val tickerView: TextView = view.findViewById(R.id.tickerView)
            val priceView: TextView = view.findViewById(R.id.priceView)

            val volumeTodayView: TextView = view.findViewById(R.id.volumeSharesView)
            val volumeTodayCashView: TextView = view.findViewById(R.id.volumeCashView)

            val changePriceAbsoluteView: TextView = view.findViewById(R.id.priceChangeAbsoluteView)
            val changePricePercentView: TextView = view.findViewById(R.id.priceChangePercentView)

            val checkBoxView: CheckBox = view.findViewById(R.id.chooseView)
        }
    }
}