package com.project.ti2358.ui.portfolio

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.DepositManager
import com.project.ti2358.data.model.dto.PortfolioPosition
import com.project.ti2358.service.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class PortfolioFragment : Fragment() {

    val depositManager: DepositManager by inject()
    var adapterList: ItemPortfolioRecyclerViewAdapter = ItemPortfolioRecyclerViewAdapter(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_portfolio_item_list, container, false)
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

        val buttonUpdate = view.findViewById<Button>(R.id.buttonUpdate)
        buttonUpdate.setOnClickListener {
            GlobalScope.launch(Dispatchers.Main) {
                depositManager.refreshDeposit()
                adapterList.setData(depositManager.portfolioPositions)
            }
            adapterList.setData(depositManager.portfolioPositions)
        }

        adapterList.setData(depositManager.portfolioPositions)

        GlobalScope.launch(Dispatchers.Main) {
            depositManager.refreshDeposit()
            adapterList.setData(depositManager.portfolioPositions)
        }
        return view
    }

    class ItemPortfolioRecyclerViewAdapter(
        private var values: List<PortfolioPosition>
    ) : RecyclerView.Adapter<ItemPortfolioRecyclerViewAdapter.ViewHolder>() {

        fun setData(newValues: List<PortfolioPosition>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fragment_portfolio_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.tickerView.text = "${position}. ${item.ticker}"

            val avg = item.getAveragePrice()
            holder.volumePiecesView.text = "${item.lots} шт."
            holder.priceView.text = "${avg}"

            val profit = item.getProfitAmount()
            holder.changePriceAbsoluteView.text = "%.2f $".format(profit)

            var totalCash = item.balance * avg
            val percent = item.getProfitPercent()
            holder.changePricePercentView.text = "%.2f".format(percent) + "%"

            totalCash += profit
            holder.volumeCashView.text = "%.2f $".format(totalCash)

            if (percent < 0) {
                holder.changePriceAbsoluteView.setTextColor(Utils.RED)
                holder.changePricePercentView.setTextColor(Utils.RED)
            } else {
                holder.changePriceAbsoluteView.setTextColor(Utils.GREEN)
                holder.changePricePercentView.setTextColor(Utils.GREEN)
            }
        }

        override fun getItemCount(): Int = values.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tickerView: TextView = view.findViewById(R.id.stock_item_ticker)
            val priceView: TextView = view.findViewById(R.id.stock_item_price)

            val volumeCashView: TextView = view.findViewById(R.id.stock_item_volume_cash)
            val volumePiecesView: TextView = view.findViewById(R.id.stock_item_volume_pieces)

            val changePriceAbsoluteView: TextView = view.findViewById(R.id.stock_item_price_change_absolute)
            val changePricePercentView: TextView = view.findViewById(R.id.stock_item_price_change_percent)
        }
    }
}