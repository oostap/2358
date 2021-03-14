package com.project.ti2358.ui.orders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.DepositManager
import com.project.ti2358.data.model.dto.OperationType
import com.project.ti2358.data.model.dto.Order
import com.project.ti2358.service.Utils
import com.project.ti2358.service.toMoney
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class OrdersFragment : Fragment() {

    val depositManager: DepositManager by inject()
    var adapterList: ItemOrdersRecyclerViewAdapter = ItemOrdersRecyclerViewAdapter(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_orders_item_list, container, false)
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
                depositManager.refreshOrders()
                updateData()
            }
        }

        val buttonCancel = view.findViewById<Button>(R.id.buttonCancel)
        buttonCancel.setOnClickListener {
            GlobalScope.launch(Dispatchers.Main) {
                depositManager.cancelAllOrders()
                updateData()
            }
        }

        GlobalScope.launch(Dispatchers.Main) {
            while (true) {
                if (depositManager.refreshOrders()) {
                    updateData()
                    break
                }
                delay(500)
            }
        }
        return view
    }

    fun updateData() {
        adapterList.setData(depositManager.orders)
        updateTitle()
    }

    private fun updateTitle() {
        val act = requireActivity() as AppCompatActivity
        act.supportActionBar?.title = "Заявки ${depositManager.orders.size}"
    }

    inner class ItemOrdersRecyclerViewAdapter(
        private var values: List<Order>
    ) : RecyclerView.Adapter<ItemOrdersRecyclerViewAdapter.ViewHolder>() {

        fun setData(newValues: List<Order>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fragment_orders_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.order = item
            holder.tickerView.text = "${position + 1}) ${item.stock?.instrument?.ticker}"
            holder.lotsView.text = "${item.executedLots} / ${item.requestedLots} шт."
            holder.priceView.text = item.price.toMoney(item.stock)

            holder.typeView.text = item.getOperationStatusString()

            if (item.operation == OperationType.BUY) {
                holder.typeView.setTextColor(Utils.RED)
            } else {
                holder.typeView.setTextColor(Utils.GREEN)
            }

            holder.buttonCancel.setOnClickListener {
                GlobalScope.launch(Dispatchers.Main) {
                    depositManager.cancelOrder(holder.order)
                    depositManager.refreshOrders()
                    updateData()
                }
            }
        }

        override fun getItemCount(): Int = values.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            lateinit var order: Order
            val tickerView: TextView = view.findViewById(R.id.stock_item_ticker)
            val typeView: TextView = view.findViewById(R.id.order_type)

            val lotsView: TextView = view.findViewById(R.id.stock_count)
            val priceView: TextView = view.findViewById(R.id.stock_price)

            val buttonCancel: Button = view.findViewById(R.id.buttonCancel)
        }
    }
}