package com.project.ti2358.ui.orderbook

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.*
import android.view.View.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.OrderbookManager
import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.manager.StockManager
import com.project.ti2358.service.log
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension


class CustomLayoutManager(context: Context?) : LinearLayoutManager(context) {
    private var isScrollEnabled = true
    fun setScrollEnabled(flag: Boolean) {
        isScrollEnabled = flag
    }

    override fun canScrollVertically(): Boolean {
        return isScrollEnabled && super.canScrollVertically()
    }
}

@KoinApiExtension
class OrderbookFragment : Fragment() {
    val stockManager: StockManager by inject()
    val orderbookManager: OrderbookManager by inject()
    var adapterList: ItemOrderbookRecyclerViewAdapter = ItemOrderbookRecyclerViewAdapter(emptyList())
    var activeStock: Stock? = null
    var orderbookLines: MutableList<OrderbookLine> = mutableListOf()
    var job: Job? = null
    lateinit var customLayoutManager: CustomLayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        job?.cancel()
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
            layoutManager = CustomLayoutManager(context)
            adapter = adapterList
            customLayoutManager = layoutManager as CustomLayoutManager
        }

        val buttonUpdate = view.findViewById<Button>(R.id.buttonUpdate)
        buttonUpdate.setOnClickListener {
            updateData()
        }

        activeStock = orderbookManager.activeStock

        // старт апдейта заявок
        job = GlobalScope.launch(Dispatchers.Main) {
            while (true) {
                delay(1000)
                updateData()
            }
        }

        updateData()
        return view
    }

    private fun updateData() {
        orderbookLines = orderbookManager.process()
        adapterList.setData(orderbookLines)

        val ticker = activeStock?.instrument?.ticker ?: ""
        val act = requireActivity() as AppCompatActivity
        act.supportActionBar?.title = getString(R.string.menu_orderbook) + " $ticker"
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

            holder.orderBuy1View.setOnTouchListener(ChoiceTouchListener())
            holder.orderBuy2View.setOnTouchListener(ChoiceTouchListener())
            holder.orderBuy3View.setOnTouchListener(ChoiceTouchListener())

            holder.orderSell1View.setOnTouchListener(ChoiceTouchListener())
            holder.orderSell2View.setOnTouchListener(ChoiceTouchListener())
            holder.orderSell3View.setOnTouchListener(ChoiceTouchListener())

            holder.dragToBuyView.setOnDragListener(ChoiceDragListener())
            holder.dragToSellSellView.setOnDragListener(ChoiceDragListener())

            holder.bidCountView.text = "${item.bidCount}"
            holder.bidPriceView.text = "%.2f".format(item.bidPrice)
            holder.askCountView.text = "${item.askCount}"
            holder.askPriceView.text = "%.2f".format(item.askPrice)

            holder.bidBackgroundView.layoutParams.width = (item.bidPercent * 500).toInt()
            holder.askBackgroundView.layoutParams.width = (item.askPercent * 500).toInt()

            holder.ordersSellsView.visibility = View.GONE
            holder.ordersBuysView.visibility = View.GONE
//            holder.tickerView.text = "${position + 1}) ${item.instrument.ticker}"
//            holder.priceView.text = "${item.getPrice2359String()} ➡ ${item.getPriceString()}"

//            holder.itemView.setOnClickListener {
//                Utils.openTinkoffForTicker(requireContext(), holder.stock.instrument.ticker)
//            }
        }

        override fun getItemCount(): Int = values.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            lateinit var orderbookLine: OrderbookLine
            val dragToBuyView: LinearLayout = view.findViewById(R.id.stock_drag_to_buy)
            val dragToSellSellView: LinearLayout = view.findViewById(R.id.stock_drag_to_sell)

            val bidBackgroundView: RelativeLayout = view.findViewById(R.id.stock_background_bid)
            val bidCountView: TextView = view.findViewById(R.id.stock_count_bid)
            val bidPriceView: TextView = view.findViewById(R.id.stock_price_bid)

            val askBackgroundView: RelativeLayout = view.findViewById(R.id.stock_background_ask)
            val askCountView: TextView = view.findViewById(R.id.stock_count_ask)
            val askPriceView: TextView = view.findViewById(R.id.stock_price_ask)

            val ordersSellsView: LinearLayout = view.findViewById(R.id.stock_orders_sell)
            val ordersBuysView: LinearLayout = view.findViewById(R.id.stock_orders_buy)

            val orderBuy1View: TextView = view.findViewById(R.id.stock_order_buy_1)
            val orderBuy2View: TextView = view.findViewById(R.id.stock_order_buy_2)
            val orderBuy3View: TextView = view.findViewById(R.id.stock_order_buy_2)

            val orderSell1View: TextView = view.findViewById(R.id.stock_order_sell_1)
            val orderSell2View: TextView = view.findViewById(R.id.stock_order_sell_2)
            val orderSell3View: TextView = view.findViewById(R.id.stock_order_sell_3)
        }

        private inner class ChoiceTouchListener : OnTouchListener {
            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
                return if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                    val data = ClipData.newPlainText("", "")
                    val shadowBuilder = DragShadowBuilder(view)
                    view.startDrag(data, shadowBuilder, view, 0)
                    true
                } else {
                    false
                }
            }
        }

        private inner class ChoiceDragListener : OnDragListener {
            override fun onDrag(v: View, event: DragEvent): Boolean {
                log("onDrag")
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> {
                    }
                    DragEvent.ACTION_DRAG_ENTERED -> {
                    }
                    DragEvent.ACTION_DRAG_EXITED -> {
                    }
                    DragEvent.ACTION_DROP -> {
                        val view = event.localState as View
                        val dropTarget = v as TextView
                        val dropped = view as TextView
                        //checking whether first character of dropTarget equals first character of dropped
//                        if (dropTarget.text.toString()[0] == dropped.text.toString()[0]) {
                        //stop displaying the view where it was before it was dragged
                        view.setVisibility(View.INVISIBLE)
                        //update the text in the target view to reflect the data being dropped
                        dropTarget.text = dropTarget.text.toString() + dropped.text.toString()
                        //make it bold to highlight the fact that an item has been dropped
                        dropTarget.setTypeface(Typeface.DEFAULT_BOLD)
                        //if an item has already been dropped here, there will be a tag
                        val tag = dropTarget.tag
                        //if there is already an item here, set it back visible in its original place
                        if (tag != null) {
                            //the tag is the view id already dropped here
                            val existingID = tag as Int
                            //set the original view visible again
                            //findViewById(existingID).setVisibility(View.VISIBLE)
                        }
                        //set the tag in the target view being dropped on - to the ID of the view being dropped
                        dropTarget.tag = dropped.id
                        //remove setOnDragListener by setting OnDragListener to null, so that no further drag & dropping on this TextView can be done
//                        customLayoutManager.setScrollEnabled(true)
//                            dropTarget.setOnDragListener(null)
//                        } else  //displays message if first character of dropTarget is not equal to first character of dropped
//                            Toast.makeText(
//                                requireContext(),
//                                dropTarget.text.toString() + "is not " + dropped.text.toString(),
//                                Toast.LENGTH_LONG
//                            ).show()
                    }
                    DragEvent.ACTION_DRAG_ENDED -> {
                    }
                    else -> {
                    }
                }
                return true
            }
        }
    }
}