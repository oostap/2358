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
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.*
import com.project.ti2358.data.model.dto.OperationType
import com.project.ti2358.data.model.dto.Order
import com.project.ti2358.data.model.dto.PortfolioPosition
import com.project.ti2358.databinding.FragmentOrderbookBinding
import com.project.ti2358.service.Utils
import com.project.ti2358.service.log
import com.project.ti2358.service.toMoney
import com.project.ti2358.service.toPercent
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.lang.Exception
import java.lang.StrictMath.min
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

@KoinApiExtension
class OrderbookFragment : Fragment(R.layout.fragment_orderbook) {
    val chartManager: ChartManager by inject()
    val stockManager: StockManager by inject()
    val orderbookManager: OrderbookManager by inject()
    val depositManager: DepositManager by inject()

    private var fragmentOrderbookBinding: FragmentOrderbookBinding? = null

    var adapterList: ItemOrderbookRecyclerViewAdapter = ItemOrderbookRecyclerViewAdapter(emptyList())
    var activeStock: Stock? = null
    var orderbookLines: MutableList<OrderbookLine> = mutableListOf()
    var jobRefreshOrders: Job? = null
    var jobRefreshOrderbook: Job? = null

    override fun onDestroy() {
        jobRefreshOrders?.cancel()
        jobRefreshOrderbook?.cancel()
        fragmentOrderbookBinding = null
        orderbookManager.stop()
        super.onDestroy()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentOrderbookBinding.bind(view)
        fragmentOrderbookBinding = binding

        with(binding) {
            list.addItemDecoration(DividerItemDecoration(binding.list.context, DividerItemDecoration.VERTICAL))
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapterList

            volumesView.children.forEach { it.visibility = GONE }
            buyPlusView.children.forEach { it.visibility = GONE }
            buyMinusView.children.forEach { it.visibility = GONE }
            sellPlusView.children.forEach { it.visibility = GONE }
            sellMinusView.children.forEach { it.visibility = GONE }

            val volumes = SettingsManager.getOrderbookVolumes().split(" ")
            var size = min(volumesView.childCount, volumes.size)
            volumesView.visibility = GONE
            for (i in 0 until size) {
                if (volumes[i] == "") continue

                volumesView.getChildAt(i).apply {
                    this as TextView

                    text = volumes[i]
                    visibility = VISIBLE

                    setOnTouchListener { v, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) v.alpha = 0.5F
                        if (event.action == MotionEvent.ACTION_UP) {
                            v.alpha = 1.0F
                            volumeEditText.setText(this.text)
                        }
                        true
                    }
                }
                volumesView.visibility = VISIBLE
            }

            val changes = SettingsManager.getOrderbookPrices().split(" ")
            size = min(buyPlusView.childCount, changes.size)
            pricesView.visibility = GONE
            for (i in 0 until size) {
                if (changes[i] == "") continue

                buyPlusView.getChildAt(i).apply {
                    this as TextView
                    text = "+${changes[i]}"
                    visibility = VISIBLE

                    setOnTouchListener { v, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) v.alpha = 0.5F
                        if (event.action == MotionEvent.ACTION_UP) {
                            v.alpha = 1.0F
                            changeOrders(this, OperationType.BUY)
                        }
                        true
                    }
                }
                sellPlusView.getChildAt(i).apply {
                    this as TextView
                    text = "+${changes[i]}"
                    visibility = VISIBLE

                    setOnTouchListener { v, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) v.alpha = 0.5F
                        if (event.action == MotionEvent.ACTION_UP) {
                            v.alpha = 1.0F
                            changeOrders(this, OperationType.SELL)
                        }
                        true
                    }
                }
                buyMinusView.getChildAt(i).apply {
                    this as TextView
                    text = "-${changes[i]}"
                    visibility = VISIBLE

                    setOnTouchListener { v, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) v.alpha = 0.5F
                        if (event.action == MotionEvent.ACTION_UP) {
                            v.alpha = 1.0F
                            changeOrders(this, OperationType.BUY)
                        }
                        true
                    }
                }
                sellMinusView.getChildAt(i).apply {
                    this as TextView
                    text = "-${changes[i]}"
                    visibility = VISIBLE

                    setOnTouchListener { v, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) v.alpha = 0.5F
                        if (event.action == MotionEvent.ACTION_UP) {
                            v.alpha = 1.0F
                            changeOrders(this, OperationType.SELL)
                        }
                        true
                    }
                }
                pricesView.visibility = VISIBLE
            }

            trashButton.setOnDragListener(ChoiceDragListener())
            trashButton.setTag(R.string.action_type, "remove")

            scalperPanelView.visibility = VISIBLE
            trashButton.visibility = GONE

            activeStock = orderbookManager.activeStock

            chartButton.setOnClickListener {
                activeStock?.let {
                    chartManager.start(it)
                    view.findNavController().navigate(R.id.action_nav_orderbook_to_nav_chart)
                }
            }

            positionView.setOnClickListener {
                activeStock?.let {
                    depositManager.getPositionForFigi(it.figi)?.let { p ->
                        volumeEditText.setText(abs(p.lots).toString())
                    }
                }
            }

            jobRefreshOrders?.cancel()
            jobRefreshOrders = GlobalScope.launch(Dispatchers.Main) {
                while (true) {
                    delay(5000)
                    depositManager.refreshOrders()
                    depositManager.refreshDeposit()
                    updatePosition()
                }
            }

            jobRefreshOrderbook?.cancel()
            jobRefreshOrderbook = GlobalScope.launch(Dispatchers.Main) {
                depositManager.refreshOrders()

                while (true) {
                    delay(1000)
                    updateData()
                    updatePosition()
                }
            }

            updateData()
            updatePosition()
        }
    }

    private fun updatePosition() {
        fragmentOrderbookBinding?.apply {
            activeStock?.let { stock ->
                positionView.visibility = GONE
                val pos = depositManager.getPositionForFigi(stock.figi)?.let { p ->
                    val avg = p.getAveragePrice()
                    priceView.text = "${avg.toMoney(stock)} ➡ ${stock.getPriceString()}"

                    val profit = p.getProfitAmount()
                    priceChangeAbsoluteView.text = profit.toMoney(stock)

                    val percent = p.getProfitPercent() * sign(p.lots.toDouble())   // инвертировать доходность шорта
                    val totalCash = p.balance * avg + profit
                    cashView.text = totalCash.toMoney(stock)

                    lotsView.text = "${p.lots}"
                    lotsBlockedView.text = "${p.blocked.toInt()}🔒"

                    priceChangePercentView.text = percent.toPercent()

                    priceView.setTextColor(Utils.getColorForValue(percent))
                    priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(percent))
                    priceChangePercentView.setTextColor(Utils.getColorForValue(percent))
                    positionView.visibility = VISIBLE
                }
            }
        }
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
        activeStock?.let {
            val buyOrders = depositManager.getOrderAllOrdersForFigi(it.figi, operationType)
            buyOrders.forEach { order ->
                val newIntPrice = (order.price * 100).roundToInt() + delta
                val newPrice: Double = Utils.makeNicePrice(newIntPrice / 100.0)
                orderbookManager.replaceOrder(order, newPrice, operationType)
            }
        }
    }

    private fun getActiveVolume(): Int {
        try {
            return fragmentOrderbookBinding?.volumeEditText?.text.toString().toInt()
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
            orderbookManager.createOrder(orderbookLine.stock.figi, price, volume, operationType)
            return
        }

        priceBox.hint = "цены"
        layout.addView(priceBox) // Notice this is an add method

        val lotsBox = EditText(context)
        lotsBox.inputType = InputType.TYPE_CLASS_NUMBER
        lotsBox.hint = "количество"
        layout.addView(lotsBox) // Another add method

        var title = if (operationType == OperationType.BUY) "КУПИТЬ!" else "ПРОДАТЬ!"
        val position = depositManager.getPositionForFigi(orderbookLine.stock.figi)
        val depoCount = position?.lots ?: 0
        val avg = position?.getAveragePrice() ?: 0
        title += " депо: $depoCount, $avg"

        val alert: android.app.AlertDialog.Builder = android.app.AlertDialog.Builder(requireContext())
        alert.setIcon(R.drawable.ic_hammer).setTitle(title).setView(layout).setPositiveButton("ок",
            DialogInterface.OnClickListener { dialog, whichButton ->
                try {
                    val price = Utils.makeNicePrice(priceBox.text.toString().toDouble())
                    val lots = lotsBox.text.toString().toInt()
                    orderbookManager.createOrder(orderbookLine.stock.figi, price, lots, operationType)
                } catch (e: Exception) {
                    Utils.showMessageAlert(requireContext(), "Неверный формат чисел!")
                }
            }).setNegativeButton("отмена",
            DialogInterface.OnClickListener { dialog, whichButton ->

            })
        alert.show()
    }

    private fun updateData() {
        if (!isVisible) return
        orderbookLines = orderbookManager.process()
        adapterList.setData(orderbookLines)

        val ticker = activeStock?.instrument?.ticker ?: ""
        val act = requireActivity() as AppCompatActivity
        act.supportActionBar?.title = getString(R.string.menu_orderbook) + " $ticker"
    }

    inner class ChoiceDragListener : OnDragListener {
        override fun onDrag(v: View, event: DragEvent): Boolean {
            log("onDrag")
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> { }
                DragEvent.ACTION_DRAG_ENTERED -> {
                    val view = event.localState as View
                    val actionType = v.getTag(R.string.action_type) as String
                    if (actionType == "replace") {
                        if (v is LinearLayout) { // строка, куда кидаем заявку
                            val position = v.getTag(R.string.position_line) as Int
                            v.setBackgroundColor(Utils.LIGHT)
                        }
                    }
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    val view = event.localState as View
                    val actionType = v.getTag(R.string.action_type) as String
                    if (actionType == "replace") {
                        if (v is LinearLayout) { // строка, куда кидаем заявку
                            val position = v.getTag(R.string.position_line) as Int
                            v.setBackgroundColor(Utils.getColorForIndex(position))
                        }
                    }
                }
                DragEvent.ACTION_DROP -> {
                    val view = event.localState as View
                    val actionType = v.getTag(R.string.action_type) as String
                    if (actionType == "replace") {
                        if (v is LinearLayout) { // строка, куда кидаем заявку
                            val dropped = view as TextView              // заявка

                            val lineTo = v.getTag(R.string.order_line) as OrderbookLine
                            val operationTo = v.getTag(R.string.order_type) as OperationType

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

                    fragmentOrderbookBinding?.scalperPanelView?.visibility = View.VISIBLE
                    fragmentOrderbookBinding?.trashButton?.visibility = View.GONE
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

            holder.dragToBuyView.setBackgroundColor(Utils.getColorForIndex(position))
            holder.dragToSellView.setBackgroundColor(Utils.getColorForIndex(position))

            holder.dragToBuyView.setOnDragListener(ChoiceDragListener())
            holder.dragToSellView.setOnDragListener(ChoiceDragListener())

            holder.dragToBuyView.setTag(R.string.position_line, position)
            holder.dragToBuyView.setTag(R.string.order_line, item)
            holder.dragToBuyView.setTag(R.string.order_type, OperationType.BUY)
            holder.dragToBuyView.setTag(R.string.action_type, "replace")

            holder.dragToSellView.setTag(R.string.position_line, position)
            holder.dragToSellView.setTag(R.string.order_line, item)
            holder.dragToSellView.setTag(R.string.order_type, OperationType.SELL)
            holder.dragToSellView.setTag(R.string.action_type, "replace")

            holder.bidCountView.text = "${item.bidCount}"
            holder.bidPriceView.text = "%.2f".format(locale = Locale.US, item.bidPrice)
            holder.askCountView.text = "${item.askCount}"
            holder.askPriceView.text = "%.2f".format(locale = Locale.US, item.askPrice)

            var targetPriceAsk = 0.0
            var targetPriceBid = 0.0
            var portfolioPosition: PortfolioPosition? = null
            activeStock?.let {
                targetPriceAsk = values.first().askPrice
                targetPriceBid = values.first().bidPrice

                portfolioPosition = depositManager.getPositionForFigi(it.figi)
                portfolioPosition?.let { p ->
                    targetPriceAsk = p.getAveragePrice()
                    targetPriceBid = p.getAveragePrice()
                }
            }

            val allow = true
            if (targetPriceAsk != 0.0 && allow) {
                val percentBid = Utils.getPercentFromTo(item.bidPrice, targetPriceBid)
                holder.bidPricePercentView.text = "%.2f%%".format(locale = Locale.US, percentBid)
                holder.bidPricePercentView.setTextColor(Utils.getColorForValue(percentBid))

                val percentAsk = Utils.getPercentFromTo(item.askPrice, targetPriceAsk)
                holder.askPricePercentView.text = "%.2f%%".format(locale = Locale.US, percentAsk)
                holder.askPricePercentView.setTextColor(Utils.getColorForValue(percentAsk))
                holder.askPricePercentView.visibility = VISIBLE
                holder.bidPricePercentView.visibility = VISIBLE

                if (percentBid == 0.0 && portfolioPosition != null) {
                    holder.dragToBuyView.setBackgroundColor(Utils.TEAL)
                }
                if (percentAsk == 0.0 && portfolioPosition != null) {
                    holder.dragToSellView.setBackgroundColor(Utils.TEAL)
                }
            } else {
                holder.askPricePercentView.visibility = GONE
                holder.bidPricePercentView.visibility = GONE
            }

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

            holder.dragToSellView.setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    v.setBackgroundColor(Utils.LIGHT)
                }
                if (event.action == MotionEvent.ACTION_UP) {
                    holder.dragToSellView.setBackgroundColor(Utils.getColorForIndex(position))
                    showEditOrder(holder.orderbookLine, OperationType.SELL)
                }
                true
            }

            holder.dragToBuyView.setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    v.setBackgroundColor(Utils.LIGHT)
                }
                if (event.action == MotionEvent.ACTION_UP) {
                    holder.dragToSellView.setBackgroundColor(Utils.getColorForIndex(position))
                    showEditOrder(holder.orderbookLine, OperationType.BUY)
                }
                true
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
            val bidPricePercentView: TextView = view.findViewById(R.id.stock_price_bid_percent)

            val askBackgroundView: RelativeLayout = view.findViewById(R.id.stock_background_ask)
            val askCountView: TextView = view.findViewById(R.id.stock_count_ask)
            val askPriceView: TextView = view.findViewById(R.id.stock_price_ask)
            val askPricePercentView: TextView = view.findViewById(R.id.stock_price_ask_percent)

            val orderBuy1View: TextView = view.findViewById(R.id.stock_order_buy_1)
            val orderBuy2View: TextView = view.findViewById(R.id.stock_order_buy_2)
            val orderBuy3View: TextView = view.findViewById(R.id.stock_order_buy_3)
            val orderBuy4View: TextView = view.findViewById(R.id.stock_order_buy_4)

            val orderSell1View: TextView = view.findViewById(R.id.stock_order_sell_1)
            val orderSell2View: TextView = view.findViewById(R.id.stock_order_sell_2)
            val orderSell3View: TextView = view.findViewById(R.id.stock_order_sell_3)
            val orderSell4View: TextView = view.findViewById(R.id.stock_order_sell_4)
        }

        inner class ChoiceTouchListener : OnTouchListener {
            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
                return if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                    val data = ClipData.newPlainText("", "")
                    val shadowBuilder = DragShadowBuilder(view)
                    view.startDrag(data, shadowBuilder, view, 0)
                    fragmentOrderbookBinding?.scalperPanelView?.visibility = View.GONE
                    fragmentOrderbookBinding?.trashButton?.visibility = View.VISIBLE
                    true
                } else {
                    false
                }
            }
        }
    }
}