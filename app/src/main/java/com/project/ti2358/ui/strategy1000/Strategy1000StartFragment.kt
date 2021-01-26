package com.project.ti2358.ui.strategy1000

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.model.dto.PortfolioPosition
import com.project.ti2358.data.service.DepoManager
import com.project.ti2358.data.service.Strategy1000
import com.project.ti2358.service.Utils
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension


@KoinApiExtension
class Strategy1000StartFragment : Fragment() {

    val strategy1000: Strategy1000 by inject()
    val depoManager: DepoManager by inject()
    var adapterList: ItemPortfolioRecyclerViewAdapter = ItemPortfolioRecyclerViewAdapter(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_1000_start, container, false)
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

        val buttonStart = view.findViewById<Button>(R.id.buttonStart)
        buttonStart.setOnClickListener {
            if (strategy1000.positionsSelected.isNotEmpty()) {
                view.findNavController().navigate(R.id.action_nav_1000_start_to_nav_1000_finish)
            } else {
                Utils.showErrorAlert(requireContext())
            }
        }

        val buttonUpdate = view.findViewById<Button>(R.id.buttonUpdate)
        buttonUpdate.setOnClickListener {
            adapterList.setData(depoManager.portfolioPositions)
        }

        adapterList.setData(depoManager.portfolioPositions)

        return view
    }

    inner class ItemPortfolioRecyclerViewAdapter(
        private var values: List<PortfolioPosition>
    ) : RecyclerView.Adapter<ItemPortfolioRecyclerViewAdapter.ViewHolder>() {

        fun setData(newValues: List<PortfolioPosition>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fragment_1000_start_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.position = item

            holder.tickerView.text = "${position}. ${item.ticker}"

            holder.checkBoxView.setOnCheckedChangeListener(null)
            holder.checkBoxView.isChecked = strategy1000.isSelected(item)

            var avg = item.getAveragePrice()
            holder.volumePiecesView.text = "${item.lots} шт."
            holder.priceView.text = "${avg}"

            var profit = item.getProfitAmount()
            holder.changePriceAbsoluteView.text = "${profit} $"

            var totalCash = item.balance * avg
            val percent = item.getProfitPercent()
            holder.changePricePercentView.text = "%.2f".format(percent) + "%"

            totalCash += profit
            holder.volumeCashView.text = "%.2f $".format(totalCash)

            if (profit < 0) {
                holder.changePriceAbsoluteView.setTextColor(Utils.RED)
                holder.changePricePercentView.setTextColor(Utils.RED)
            } else {
                holder.changePriceAbsoluteView.setTextColor(Utils.GREEN)
                holder.changePricePercentView.setTextColor(Utils.GREEN)
            }

            holder.checkBoxView.setOnCheckedChangeListener { _, isChecked ->
                strategy1000.setSelected(holder.position, !isChecked)
            }

            holder.itemView.setOnClickListener {
                holder.position.stock?.let {
                    Utils.openTinkoffForTicker(requireContext(), it.marketInstrument.ticker)
                }
            }
        }

        override fun getItemCount(): Int = values.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            lateinit var position: PortfolioPosition

            val tickerView: TextView = view.findViewById(R.id.stock_item_ticker)
            val priceView: TextView = view.findViewById(R.id.stock_item_price)

            val volumeCashView: TextView = view.findViewById(R.id.stock_item_volume_cash)
            val volumePiecesView: TextView = view.findViewById(R.id.stock_item_volume_pieces)

            val changePriceAbsoluteView: TextView = view.findViewById(R.id.stock_item_price_change_absolute)
            val changePricePercentView: TextView = view.findViewById(R.id.stock_item_price_change_percent)

            val checkBoxView: CheckBox = view.findViewById(R.id.check_box)

            override fun toString(): String {
                return super.toString() + " '" + tickerView.text + "'"
            }
        }
    }
}