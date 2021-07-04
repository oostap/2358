package com.project.ti2358.ui.orders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.ChartManager
import com.project.ti2358.data.manager.PortfolioManager
import com.project.ti2358.data.manager.OrderbookManager
import com.project.ti2358.data.model.dto.Order
import com.project.ti2358.databinding.FragmentOrdersBinding
import com.project.ti2358.databinding.FragmentOrdersItemBinding
import com.project.ti2358.service.Utils
import com.project.ti2358.service.toMoney
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class OrdersFragment : Fragment(R.layout.fragment_orders) {
    private val orderbookManager: OrderbookManager by inject()
    private val chartManager: ChartManager by inject()

    val portfolioManager: PortfolioManager by inject()

    private var fragmentOrdersBinding: FragmentOrdersBinding? = null

    var adapterList: ItemOrdersRecyclerViewAdapter = ItemOrdersRecyclerViewAdapter(emptyList())
    var jobRefreshEndless: Job? = null
    var jobCancelAll: Job? = null
    var jobCancel: Job? = null
    var jobRefresh: Job? = null

    override fun onDestroy() {
        fragmentOrdersBinding = null
        jobCancelAll?.cancel()
        jobRefreshEndless?.cancel()
        jobRefresh?.cancel()
        jobCancel?.cancel()
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentOrdersBinding.bind(view)
        fragmentOrdersBinding = binding

        with(binding) {
            list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapterList

            updateButton.setOnClickListener {
                jobRefresh?.cancel()
                jobRefresh = GlobalScope.launch(Dispatchers.Main) {
                    portfolioManager.refreshOrders()
                    updateData()
                }
            }

            cancelButton.setOnClickListener {
                jobCancelAll?.cancel()
                jobCancelAll = GlobalScope.launch(Dispatchers.Main) {
                    orderbookManager.cancelAllOrders()
                    updateData()
                }
            }
        }

        jobRefreshEndless?.cancel()
        jobRefreshEndless = GlobalScope.launch(Dispatchers.Main) {
            while (true) {
                if (portfolioManager.refreshOrders()) {
                    updateData()
                    break
                }
                delay(5000)
            }
        }
    }

    fun updateData() {
        adapterList.setData(portfolioManager.orders)
        updateTitle()
    }

    private fun updateTitle() {
        if (isAdded) {
            val act = requireActivity() as AppCompatActivity
            act.supportActionBar?.title = "Заявки ${portfolioManager.orders.size}"
        }
    }

    inner class ItemOrdersRecyclerViewAdapter(private var values: List<Order>) : RecyclerView.Adapter<ItemOrdersRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<Order>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FragmentOrdersItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ItemOrdersRecyclerViewAdapter.ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: FragmentOrdersItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val order = values[index]
                with(binding) {
                    tickerView.text = "${index + 1}) ${order.stock?.getTickerLove()}"
                    lotsView.text = "${order.executedLots} / ${order.requestedLots} шт."
                    priceView.text = order.price.toMoney(order.stock)

                    orderTypeView.text = order.getOperationStatusString()
                    orderTypeView.setTextColor(Utils.getColorForOperation(order.operation))

                    cancelButton.setOnClickListener {
                        jobCancel?.cancel()
                        jobCancel = GlobalScope.launch(Dispatchers.Main) {
                            orderbookManager.cancelOrder(order)
                            updateData()
                        }
                    }

                    itemView.setOnClickListener {
                        order.stock?.let {
                            Utils.openOrderbookForStock(findNavController(), orderbookManager, it)
                        }
                    }

                    chartButton.setOnClickListener {
                        order.stock?.let {
                            Utils.openChartForStock(findNavController(), chartManager, it)
                        }
                    }
                }
            }
        }
    }
}