package com.project.ti2358.ui.orderbook

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.OnShowListener
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.*
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.*
import com.project.ti2358.data.model.dto.OperationType
import com.project.ti2358.data.model.dto.Order
import com.project.ti2358.data.model.dto.PortfolioPosition
import com.project.ti2358.data.model.dto.pantini.PantiniPrint
import com.project.ti2358.databinding.FragmentOrderbookBinding
import com.project.ti2358.databinding.FragmentOrderbookItemBinding
import com.project.ti2358.databinding.FragmentOrderbookLentaItemBinding
import com.project.ti2358.service.*
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
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

    var orderlinesViews: MutableList<OrderlineHolder> = mutableListOf()
    var orderlinesUSViews: MutableList<OrderlineHolder> = mutableListOf()
    var orderlentaUSViews: MutableList<OrderLentaHolder> = mutableListOf()

    var activeStock: Stock? = null
    var orderbookLines: MutableList<OrderbookLine> = mutableListOf()
    var orderbookUSLines: MutableList<OrderbookLine> = mutableListOf()
    var orderbookUSLenta: MutableList<PantiniPrint> = mutableListOf()

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
            // создать лист руками
            orderlinesViews.clear()
            orderlinesUSViews.clear()
            orderlentaUSViews.clear()

            orderbookLinesView.removeAllViews()
            orderbookUsLinesView.removeAllViews()
            orderbookUsLentaView.removeAllViews()
            for (i in 0..20) {
                val orderlineHolder = OrderlineHolder(FragmentOrderbookItemBinding.inflate(LayoutInflater.from(context), null, false))
                orderbookLinesView.addView(orderlineHolder.binding.root)
                orderlinesViews.add(orderlineHolder)
            }

            for (i in 0..5) {
                val orderlineHolder = OrderlineHolder(FragmentOrderbookItemBinding.inflate(LayoutInflater.from(context), null, false))
                orderbookUsLinesView.addView(orderlineHolder.binding.root)
                orderlinesUSViews.add(orderlineHolder)
            }

            for (i in 0..50) {
                val orderlentaHolder = OrderLentaHolder(FragmentOrderbookLentaItemBinding.inflate(LayoutInflater.from(context), null, false))
                orderbookUsLentaView.addView(orderlentaHolder.binding.root)
                orderlentaUSViews.add(orderlentaHolder)
            }
            orderbookUsLentaView.visibility = GONE

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
                    Utils.openChartForStock(view.findNavController(), chartManager, it)
                }
            }

            tinkoffButton.setOnClickListener {
                activeStock?.let {
                    Utils.openTinkoffForTicker(requireContext(), it.ticker)
                }
            }

            lentaButton.setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    orderbookUsLentaView.visibility = VISIBLE
                    orderbookUsLinesView.visibility = GONE
                    orderbookLinesView.visibility = GONE
                }

                if (event.action == MotionEvent.ACTION_UP) {
                    orderbookUsLentaView.visibility = GONE
                    orderbookUsLinesView.visibility = VISIBLE
                    orderbookLinesView.visibility = VISIBLE
                }
                true
            }

            positionView.setOnClickListener {
                activeStock?.let {
                    depositManager.getPositionForFigi(it.figi)?.let { p ->
                        volumeEditText.setText(abs(p.lots - p.blocked).toInt().toString())
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
        GlobalScope.launch(Dispatchers.Main) {
            fragmentOrderbookBinding?.apply {
                activeStock?.let { stock ->
                    positionView.visibility = GONE
                    depositManager.getPositionForFigi(stock.figi)?.let { p ->
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
                val newPrice: Double = Utils.makeNicePrice(newIntPrice / 100.0, order.stock)
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
            orderbookManager.createOrder(orderbookLine.stock, price, volume, operationType)
            return
        }

        priceBox.hint = "цена"
        layout.addView(priceBox)

        val lotsBox = EditText(context)
        lotsBox.inputType = InputType.TYPE_CLASS_NUMBER
        lotsBox.hint = "количество"
        layout.addView(lotsBox)

        val position = depositManager.getPositionForFigi(orderbookLine.stock.figi)
        val depoCount = position?.lots ?: 0
        val avg = position?.getAveragePrice() ?: 0
        val title = "В депо: $depoCount по $avg"

        val alert: android.app.AlertDialog.Builder = android.app.AlertDialog.Builder(requireContext())
        alert.setIcon(R.drawable.ic_hammer).setTitle(title).setView(layout).setPositiveButton("ПРОДАТЬ",
            DialogInterface.OnClickListener { dialog, whichButton ->
                try {
                    val price = Utils.makeNicePrice(priceBox.text.toString().toDouble(), orderbookLine.stock)
                    val lots = lotsBox.text.toString().toInt()
                    orderbookManager.createOrder(orderbookLine.stock, price, lots, OperationType.SELL)
                } catch (e: Exception) {
                    Utils.showMessageAlert(requireContext(), "Неверный формат чисел!")
                }
            }).setNegativeButton("ОТМЕНА",
            DialogInterface.OnClickListener { dialog, whichButton ->

            }).setNeutralButton("КУПИТЬ",
            DialogInterface.OnClickListener { dialog, whichButton ->
                try {
                    val price = Utils.makeNicePrice(priceBox.text.toString().toDouble(), orderbookLine.stock)
                    val lots = lotsBox.text.toString().toInt()
                    orderbookManager.createOrder(orderbookLine.stock, price, lots, OperationType.BUY)
                } catch (e: Exception) {
                    Utils.showMessageAlert(requireContext(), "Неверный формат чисел!")
                }
            })

        alert.show()
    }

    private fun updateData() {
        if (!isVisible) return
        orderbookLines = orderbookManager.process()
        orderbookUSLines = orderbookManager.processUS()
        orderbookUSLenta = orderbookManager.processUSLenta()

        fragmentOrderbookBinding?.apply {
            // SPB
            orderbookLinesView.children.forEach { it.visibility = GONE }
            var size = min(orderbookLines.size, orderbookLinesView.childCount)
            for (i in 0 until size) {
                orderbookLinesView.getChildAt(i).visibility = VISIBLE
                orderlinesViews[i].updateData(orderbookLines[i], i)
            }

            // US
            orderbookUsLinesView.children.forEach { it.visibility = GONE }
            size = min(orderbookUSLines.size, orderbookUsLinesView.childCount)
            for (i in 0 until size) {
                orderbookUsLinesView.getChildAt(i).visibility = VISIBLE
                orderlinesUSViews[i].updateData(orderbookUSLines[i], i)
            }

            // US lenta
            orderbookUsLentaView.children.forEach { it.visibility = GONE }
            size = min(orderbookUSLenta.size, orderbookUsLentaView.childCount)
            for (i in 0 until size) {
                orderbookUsLentaView.getChildAt(i).visibility = VISIBLE
                orderlentaUSViews[i].updateData(orderbookUSLenta[i], i)
            }
        }

        val ticker = activeStock?.getTickerLove() ?: ""
        val act = requireActivity() as AppCompatActivity
        act.supportActionBar?.title = getString(R.string.menu_orderbook) + " $ticker"
    }

    inner class ChoiceDragListener : OnDragListener {
        override fun onDrag(v: View, event: DragEvent): Boolean {
            log("onDrag")
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                }
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
                        orderbookManager.cancelOrder(order)
                    }

                    fragmentOrderbookBinding?.scalperPanelView?.visibility = VISIBLE
                    fragmentOrderbookBinding?.trashButton?.visibility = GONE
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                }
                else -> { }
            }
            return true
        }
    }

    inner class OrderlineHolder(val binding: FragmentOrderbookItemBinding) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ClickableViewAccessibility")
        fun updateData(item: OrderbookLine, index: Int) {
            with(binding) {
                countBidView.text = "${item.bidCount}"
                priceBidView.text = item.bidPrice.toMoney(item.stock, false)

                countAskView.text = "${item.askCount}"
                priceAskView.text = item.askPrice.toMoney(item.stock, false)

                var targetPriceAsk = 0.0
                var targetPriceBid = 0.0
                var portfolioPosition: PortfolioPosition? = null
                activeStock?.let {
                    if (orderbookLines.isNotEmpty()) {
                        targetPriceAsk = orderbookLines.first().askPrice
                        targetPriceBid = orderbookLines.first().bidPrice

                        portfolioPosition = depositManager.getPositionForFigi(it.figi)
                        portfolioPosition?.let { p ->
                            targetPriceAsk = p.getAveragePrice()
                            targetPriceBid = p.getAveragePrice()
                        }
                    }
                }

                val allow = true
                if (targetPriceAsk != 0.0 && allow) {
                    var sign = 1.0
                    portfolioPosition?.let {
                        sign = sign(it.lots.toDouble())
                    }

                    val percentBid = Utils.getPercentFromTo(item.bidPrice, targetPriceBid) * sign
                    priceBidPercentView.text = "%.2f%%".format(locale = Locale.US, percentBid)
                    priceBidPercentView.setTextColor(Utils.getColorForValue(percentBid))
                    priceBidPercentView.visibility = VISIBLE

                    val percentAsk = Utils.getPercentFromTo(item.askPrice, targetPriceAsk) * sign
                    priceAskPercentView.text = "%.2f%%".format(locale = Locale.US, percentAsk)
                    priceAskPercentView.setTextColor(Utils.getColorForValue(percentAsk))
                    priceAskPercentView.visibility = VISIBLE

                    if (percentBid == 0.0 && portfolioPosition != null) {
                        dragToBuyView.setBackgroundColor(Utils.TEAL)
                    }
                    if (percentAsk == 0.0 && portfolioPosition != null) {
                        dragToSellView.setBackgroundColor(Utils.TEAL)
                    }
                } else {
                    priceAskPercentView.visibility = GONE
                    priceBidPercentView.visibility = GONE
                }

                if (item.exchange != "") { // US line
                    priceAskPercentView.text = item.exchange
                    priceBidPercentView.text = item.exchange

                    priceAskPercentView.setTextColor(Color.BLACK)
                    priceBidPercentView.setTextColor(Color.BLACK)

                    backgroundBidView.alpha = 0.4f
                    backgroundAskView.alpha = 0.4f
                } else {
                    backgroundBidView.alpha = 1.0f
                    backgroundAskView.alpha = 1.0f
                }

                backgroundBidView.startAnimation(ResizeWidthAnimation(backgroundBidView, (item.bidPercent * 1000).toInt()).apply { duration = 250 })
                backgroundAskView.startAnimation(ResizeWidthAnimation(backgroundAskView, (item.askPercent * 1000).toInt()).apply { duration = 250 })

                // ордера на покупку
                val ordersBuy = listOf(orderBuy1View, orderBuy2View, orderBuy3View, orderBuy4View)
                ordersBuy.forEach {
                    it.visibility = GONE
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
                val ordersSell = listOf(orderSell1View, orderSell2View, orderSell3View, orderSell4View)
                ordersSell.forEach {
                    it.visibility = GONE
                    it.setOnTouchListener(ChoiceTouchListener())

                }

                size = min(item.ordersSell.size, ordersSell.size)
                for (i in 0 until size) {
                    ordersSell[i].visibility = VISIBLE
                    ordersSell[i].text = "${item.ordersSell[i].requestedLots - item.ordersSell[i].executedLots}"

                    ordersSell[i].setTag(R.string.order_line, item)
                    ordersSell[i].setTag(R.string.order_item, item.ordersSell[i])
                }

                if (item.exchange == "") {
                    dragToBuyView.setBackgroundColor(Utils.getColorForIndex(index))
                    dragToSellView.setBackgroundColor(Utils.getColorForIndex(index))

                    dragToBuyView.setOnDragListener(ChoiceDragListener())
                    dragToSellView.setOnDragListener(ChoiceDragListener())

                    dragToBuyView.setTag(R.string.position_line, index)
                    dragToBuyView.setTag(R.string.order_line, item)
                    dragToBuyView.setTag(R.string.order_type, OperationType.BUY)
                    dragToBuyView.setTag(R.string.action_type, "replace")

                    dragToSellView.setTag(R.string.position_line, index)
                    dragToSellView.setTag(R.string.order_line, item)
                    dragToSellView.setTag(R.string.order_type, OperationType.SELL)
                    dragToSellView.setTag(R.string.action_type, "replace")
                }
                dragToSellView.setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        v.setBackgroundColor(Utils.LIGHT)
                    }

                    if (event.action == MotionEvent.ACTION_UP) {
                        showEditOrder(item, OperationType.SELL)
                        v.setBackgroundColor(Utils.getColorForIndex(index))
                    }
                    true
                }

                dragToBuyView.setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        v.setBackgroundColor(Utils.LIGHT)
                    }

                    if (event.action == MotionEvent.ACTION_UP) {
                        showEditOrder(item, OperationType.BUY)
                        v.setBackgroundColor(Utils.getColorForIndex(index))
                    }
                    true
                }
            }
        }
    }

    inner class OrderLentaHolder(val binding: FragmentOrderbookLentaItemBinding) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ClickableViewAccessibility")
        fun updateData(item: PantiniPrint, index: Int) {
            with(binding) {
                priceView.text = item.price.toString()
                volumeView.text = item.size.toString()
                timeView.text = item.time.toString("dd-MM HH:mm:ss")
                mmView.text = item.exchange
                conditionView.text = item.condition

                // цвета
                printView.setBackgroundColor(Utils.getColorBackgroundForPrint(item.hit))
                val textColor = Utils.getColorTextForPrint(item.hit)
                priceView.setTextColor(textColor)
                volumeView.setTextColor(textColor)
                timeView.setTextColor(textColor)
                mmView.setTextColor(textColor)
                conditionView.setTextColor(textColor)
            }
        }
    }

    class ResizeWidthAnimation(private val mView: View, private val mWidth: Int) : Animation() {
        private val mStartWidth: Int = mView.width

        override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            mView.layoutParams.width = mStartWidth + ((mWidth - mStartWidth) * interpolatedTime).toInt()
            mView.requestLayout()
        }

        override fun willChangeBounds(): Boolean {
            return true
        }
    }

    inner class ChoiceTouchListener : OnTouchListener {
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
            return if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                val data = ClipData.newPlainText("", "")
                val shadowBuilder = DragShadowBuilder(view)
                view.startDrag(data, shadowBuilder, view, 0)
                fragmentOrderbookBinding?.apply {
                    scalperPanelView.visibility = GONE
                    trashButton.visibility = VISIBLE
                }
                true
            } else {
                false
            }
        }
    }
}