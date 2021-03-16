package com.project.ti2358.ui.orderbook

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.view.View.*
import android.widget.*
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.*
import com.project.ti2358.data.model.dto.OperationType
import com.project.ti2358.data.model.dto.Order
import com.project.ti2358.service.Utils
import com.project.ti2358.service.log
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.lang.Exception
import java.lang.StrictMath.min
import kotlin.math.sign


@KoinApiExtension
class OrderbookFragment : Fragment() {
    val stockManager: StockManager by inject()
    val orderbookManager: OrderbookManager by inject()
    val depositManager: DepositManager by inject()

    var adapterList: ItemOrderbookRecyclerViewAdapter = ItemOrderbookRecyclerViewAdapter(emptyList())
    var activeStock: Stock? = null
    var orderbookLines: MutableList<OrderbookLine> = mutableListOf()
    var job1: Job? = null
    var job2: Job? = null

    lateinit var localLayoutManager: LinearLayoutManager
    lateinit var recyclerView: RecyclerView

    lateinit var imageTrash: ImageView
    // скальпер кнопки
    lateinit var scalperView: RelativeLayout
    lateinit var volumeEditText: EditText
    lateinit var volumesView: LinearLayout
    lateinit var buyPlusView: LinearLayout
    lateinit var buyMinusView: LinearLayout
    lateinit var sellPlusView: LinearLayout
    lateinit var sellMinusView: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        job1?.cancel()
        job2?.cancel()

        orderbookManager.stop()
        super.onDestroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_orderbook, container, false)
        val list = view.findViewById<RecyclerView>(R.id.list)
        list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))

        list.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = adapterList
            localLayoutManager = layoutManager as LinearLayoutManager
            recyclerView = this
        }

        scalperView = view.findViewById(R.id.scalper_panel)
        volumeEditText = view.findViewById(R.id.edit_volume)
        volumesView = view.findViewById(R.id.volumes)
        buyPlusView = view.findViewById(R.id.buy_plus)
        buyMinusView = view.findViewById(R.id.buy_minus)
        sellPlusView = view.findViewById(R.id.sell_plus)
        sellMinusView = view.findViewById(R.id.sell_minus)

        volumesView.children.forEach { it.visibility = View.GONE }
        buyPlusView.children.forEach { it.visibility = View.GONE }
        buyMinusView.children.forEach { it.visibility = View.GONE }
        sellPlusView.children.forEach { it.visibility = View.GONE }
        sellMinusView.children.forEach { it.visibility = View.GONE }

        val vols = SettingsManager.getOrderbookVolumes()
        val volumes = vols.split(" ")
        var size = min(volumesView.childCount, volumes.size)
        for (i in 0 until size) {
            volumesView.getChildAt(i).apply {
                this as TextView

                text = volumes[i]
                visibility = VISIBLE

                setOnClickListener {
                    volumeEditText.setText(this.text)
                }
            }
        }

        val p = SettingsManager.getOrderbookPrices()
        val changes = p.split(" ")
        size = min(buyPlusView.childCount, changes.size)
        for (i in 0 until size) {
            buyPlusView.getChildAt(i).apply { this as TextView
                text = "+${changes[i]}"
                visibility = VISIBLE

                setOnClickListener { changeOrders(it as TextView, OperationType.BUY) }
            }
            sellPlusView.getChildAt(i).apply { this as TextView
                text = "+${changes[i]}"
                visibility = VISIBLE

                setOnClickListener { changeOrders(it as TextView, OperationType.SELL) }
            }

            buyMinusView.getChildAt(i).apply { this as TextView
                text = "-${changes[i]}"
                visibility = VISIBLE

                setOnClickListener { changeOrders(it as TextView, OperationType.BUY) }
            }
            sellMinusView.getChildAt(i).apply { this as TextView
                text = "-${changes[i]}"
                visibility = VISIBLE

                setOnClickListener { changeOrders(it as TextView, OperationType.SELL) }
            }
        }

        imageTrash = view.findViewById(R.id.image_trash)
        imageTrash.setOnDragListener(ChoiceDragListener())
        imageTrash.setTag(R.string.action_type, "remove")

        scalperView.visibility = View.VISIBLE
        imageTrash.visibility = View.GONE

        activeStock = orderbookManager.activeStock

        job1 = GlobalScope.launch(Dispatchers.Main) {
            while (true) {
                delay(5000)
                depositManager.refreshOrders()
            }
        }

        // старт апдейта заявок
        job2 = GlobalScope.launch(Dispatchers.Main) {
            depositManager.refreshOrders()

            while (true) {
                delay(1000)
                updateData()
            }
        }

        updateData()
        return view
    }

    private fun changeOrders(textView: TextView, operationType: OperationType) {
        try {
            val text = textView.text.toString().replace("+", "")
            val change = text.toInt()
            moveAllBuyOrders(change, operationType)
        } catch (e: Exception) {

        }
    }

    private fun moveAllBuyOrders(delta: Int, operationType: OperationType) {
        val sign = if (delta < 0) 0.0 else 0.1
        activeStock?.let {
            val buyOrders = depositManager.getOrderAllOrdersForFigi(it.instrument.figi, operationType)
            buyOrders.forEach { order ->
                val newIntPrice = (order.price * 100 + sign).toInt() + delta
                val newPrice: Double = Utils.makeNicePrice(newIntPrice / 100.0)
                orderbookManager.replaceOrder(order, newPrice, operationType)
            }
        }
    }

    private fun getActiveVolume(): Int {
        try {
            return volumeEditText.text.toString().toInt()
        } catch (e: Exception) {

        }
        return 0
    }

    fun showEditOrder(orderbookLine: OrderbookLine, operationType: OperationType) {

        val context: Context = requireContext()
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL

        val priceBox = EditText(context)
        priceBox.inputType = InputType.TYPE_CLASS_PHONE
        if (operationType == OperationType.BUY) {
            priceBox.setText("${orderbookLine.bidPrice}")
        } else {
            priceBox.setText("${orderbookLine.askPrice}")
        }

        val volume = getActiveVolume()
        if (volume != 0) {
            val price = priceBox.text.toString().toDouble()
            orderbookManager.createOrder(orderbookLine.stock.instrument.figi, price, volume, operationType)
            return
        }

        priceBox.hint = "цены"
        layout.addView(priceBox) // Notice this is an add method

        val lotsBox = EditText(context)
        lotsBox.inputType = InputType.TYPE_CLASS_NUMBER
        lotsBox.hint = "количество"
        layout.addView(lotsBox) // Another add method

        var title = if (operationType == OperationType.BUY) "КУПИТЬ!" else "ПРОДАТЬ!"
        val position = depositManager.getPositionForFigi(orderbookLine.stock.instrument.figi)
        val depoCount = position?.lots ?: 0
        title += " депо: $depoCount"

        val alert: android.app.AlertDialog.Builder = android.app.AlertDialog.Builder(requireContext())
        alert.setIcon(R.drawable.ic_hammer).setTitle(title).setView(layout).setPositiveButton("ок",
            DialogInterface.OnClickListener { dialog, whichButton ->
                try {
                    val price = Utils.makeNicePrice(priceBox.text.toString().toDouble())
                    val lots = lotsBox.text.toString().toInt()
                    orderbookManager.createOrder(orderbookLine.stock.instrument.figi, price, lots, operationType)
                } catch (e: Exception) {
                    Utils.showMessageAlert(requireContext(), "Неверный формат чисел!")
                }
            }).setNegativeButton("отмена",
            DialogInterface.OnClickListener { dialog, whichButton ->

            })
        alert.show()
    }

    private fun updateData() {
        orderbookLines = orderbookManager.process()
        adapterList.setData(orderbookLines)

        val ticker = activeStock?.instrument?.ticker ?: ""
        var lots = ""
        val act = requireActivity() as AppCompatActivity

        activeStock?.let {
            depositManager.getPositionForFigi(it.instrument.figi)?.let { p ->
                lots = " ${p.blocked.toInt()}/${p.lots}"
            }
        }

        act.supportActionBar?.title = getString(R.string.menu_orderbook) + " $ticker" + lots

//        val firstVisibleItemPosition: Int = localLayoutManager.findFirstVisibleItemPosition()
//        val lastVisibleItemPosition: Int = localLayoutManager.findLastVisibleItemPosition()
//        if (lastVisibleItemPosition > 0 && firstVisibleItemPosition > 0) {
//            for (i in firstVisibleItemPosition..lastVisibleItemPosition) {
//                val holder: ItemOrderbookRecyclerViewAdapter.ViewHolder =
//                    recyclerView.findViewHolderForAdapterPosition(i) as ItemOrderbookRecyclerViewAdapter.ViewHolder
//            }
//        }
    }


    inner class ChoiceDragListener : OnDragListener {
        override fun onDrag(v: View, event: DragEvent): Boolean {
            log("onDrag")
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> { }
                DragEvent.ACTION_DRAG_ENTERED -> { }
                DragEvent.ACTION_DRAG_EXITED -> { }
                DragEvent.ACTION_DROP -> {
                    val view = event.localState as View

                    val actionType = v.getTag(R.string.action_type) as String

                    if (actionType == "replace") {
                        if (v is LinearLayout) {
                            val dropTarget = v as LinearLayout          // строка, куда кидаем заявку
                            val dropped = view as TextView              // заявка

                            val lineTo = dropTarget.getTag(R.string.order_line) as OrderbookLine
                            val operationTo = dropTarget.getTag(R.string.order_type) as OperationType

                            var lineFrom = dropped.getTag(R.string.order_line) as OrderbookLine
                            val order = dropped.getTag(R.string.order_item) as Order

                            dropped.visibility = View.INVISIBLE
                            orderbookManager.replaceOrder(order, lineTo, operationTo)
                        }
                    } else if (actionType == "remove") {
                        val dropped = view as TextView              // заявка
                        val order = dropped.getTag(R.string.order_item) as Order
                        orderbookManager.removeOrder(order)
                    }

                    scalperView.visibility = View.VISIBLE
                    imageTrash.visibility = View.GONE
                }
                DragEvent.ACTION_DRAG_ENDED -> { }
                else -> { }
            }
            return true
        }
    }

    inner class ItemOrderbookRecyclerViewAdapter(
        private var values: List<OrderbookLine>
    ) : RecyclerView.Adapter<ItemOrderbookRecyclerViewAdapter.ViewHolder>() {

        fun setData(newValues: List<OrderbookLine>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.fragment_orderbook_item, parent, false)
            return ViewHolder(view)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.orderbookLine = item

            holder.dragToBuyView.setOnDragListener(ChoiceDragListener())
            holder.dragToSellView.setOnDragListener(ChoiceDragListener())

            holder.dragToBuyView.setTag(R.string.order_line, item)
            holder.dragToBuyView.setTag(R.string.order_type, OperationType.BUY)
            holder.dragToBuyView.setTag(R.string.action_type, "replace")

            holder.dragToSellView.setTag(R.string.order_line, item)
            holder.dragToSellView.setTag(R.string.order_type, OperationType.SELL)
            holder.dragToSellView.setTag(R.string.action_type, "replace")

            holder.bidCountView.text = "${item.bidCount}"
            holder.bidPriceView.text = "%.2f".format(item.bidPrice)
            holder.askCountView.text = "${item.askCount}"
            holder.askPriceView.text = "%.2f".format(item.askPrice)

            holder.bidBackgroundView.layoutParams.width = (item.bidPercent * 500).toInt()
            holder.askBackgroundView.layoutParams.width = (item.askPercent * 500).toInt()

            // ордера на покупку
            val ordersBuy = listOf<TextView>(holder.orderBuy1View, holder.orderBuy2View, holder.orderBuy3View, holder.orderBuy4View)
            ordersBuy.forEach {
                it.visibility = View.GONE
                it.setOnTouchListener(ChoiceTouchListener())
            }

            var size = min(item.ordersBuy.size, ordersBuy.size)
            for (i in 0 until size) {
                ordersBuy[i].visibility = VISIBLE
                ordersBuy[i].text = "${item.ordersBuy[i].requestedLots - item.ordersBuy[i].executedLots}"

                ordersBuy[i].setTag(R.string.order_line, item)
                ordersBuy[i].setTag(R.string.order_item, item.ordersBuy[i])
            }

            // ордера на продажу
            val ordersSell = listOf<TextView>(holder.orderSell1View, holder.orderSell2View, holder.orderSell3View, holder.orderSell4View)
            ordersSell.forEach {
                it.visibility = View.GONE
                it.setOnTouchListener(ChoiceTouchListener())

            }

            size = min(item.ordersSell.size, ordersSell.size)
            for (i in 0 until size) {
                ordersSell[i].visibility = VISIBLE
                ordersSell[i].text = "${item.ordersSell[i].requestedLots - item.ordersSell[i].executedLots}"

                ordersSell[i].setTag(R.string.order_line, item)
                ordersSell[i].setTag(R.string.order_item, item.ordersSell[i])
            }

            holder.dragToSellView.setOnClickListener {
                showEditOrder(holder.orderbookLine, OperationType.SELL)
            }

            holder.dragToBuyView.setOnClickListener {
                showEditOrder(holder.orderbookLine, OperationType.BUY)
            }
        }

        override fun getItemCount(): Int = values.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            lateinit var orderbookLine: OrderbookLine
            val dragToBuyView: LinearLayout = view.findViewById(R.id.stock_drag_to_buy)
            val dragToSellView: LinearLayout = view.findViewById(R.id.stock_drag_to_sell)

            val bidBackgroundView: RelativeLayout = view.findViewById(R.id.stock_background_bid)
            val bidCountView: TextView = view.findViewById(R.id.stock_count_bid)
            val bidPriceView: TextView = view.findViewById(R.id.stock_price_bid)

            val askBackgroundView: RelativeLayout = view.findViewById(R.id.stock_background_ask)
            val askCountView: TextView = view.findViewById(R.id.stock_count_ask)
            val askPriceView: TextView = view.findViewById(R.id.stock_price_ask)

            val orderBuy1View: TextView = view.findViewById(R.id.stock_order_buy_1)
            val orderBuy2View: TextView = view.findViewById(R.id.stock_order_buy_2)
            val orderBuy3View: TextView = view.findViewById(R.id.stock_order_buy_3)
            val orderBuy4View: TextView = view.findViewById(R.id.stock_order_buy_4)

            val orderSell1View: TextView = view.findViewById(R.id.stock_order_sell_1)
            val orderSell2View: TextView = view.findViewById(R.id.stock_order_sell_2)
            val orderSell3View: TextView = view.findViewById(R.id.stock_order_sell_3)
            val orderSell4View: TextView = view.findViewById(R.id.stock_order_sell_4)
        }

        private inner class ChoiceTouchListener : OnTouchListener {
            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
                return if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                    val data = ClipData.newPlainText("", "")
                    val shadowBuilder = DragShadowBuilder(view)
                    view.startDrag(data, shadowBuilder, view, 0)
                    scalperView.visibility = View.GONE
                    imageTrash.visibility = View.VISIBLE
                    true
                } else {
                    false
                }
            }
        }
    }
}