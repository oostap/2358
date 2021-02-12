package com.project.ti2358.ui.strategy1830

import android.app.ActivityManager
import android.content.Context.ACTIVITY_SERVICE
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
import com.project.ti2358.data.manager.PurchaseStock
import com.project.ti2358.data.manager.Strategy1830
import com.project.ti2358.service.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class Strategy1830FinishFragment : Fragment() {

    val strategy1830: Strategy1830 by inject()
    var adapterList: Item1830RecyclerViewAdapter = Item1830RecyclerViewAdapter(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_1830_finish, container, false)
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
        if (isServiceRunning(Strategy2358Service::class.java)) {
            buttonStart.text = getString(R.string.service_2358_stop)
        } else {
            buttonStart.text = getString(R.string.service_2358_start)
        }

        buttonStart.setOnClickListener {
//            if (isServiceRunning(Strategy2358Service::class.java)) {
//                requireContext().stopService(Intent(context, Strategy2358Service::class.java))
//            } else {
//                // больше 1 штуки?
//                if (strategy2358.getTotalPurchasePieces() > 0) {
//                    Intent(context, Strategy2358Service::class.java).also {
//                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                            requireContext().startForegroundService(it)
//                        }
//                        requireContext().startService(it)
//                    }
//                }
//            }
        }


        var stocks = strategy1830.getPurchaseStock()
        adapterList.setData(stocks)

//        var time = SettingsManager.get2358PurchaseTime()
//
//        val textInfo = view.findViewById<TextView>(R.id.info_text)
//        val prepareText: String = SettingsManager.context.getString(R.string.prepare_start_2358_text)
//        textInfo.text = String.format(
//            prepareText,
//            time,
//            stocks.size,
//            strategy2358.getTotalPurchaseString()
//        )

        return view
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val context = requireContext()
        val manager = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager?
        for (service in manager!!.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    class Item1830RecyclerViewAdapter(
        private var values: List<PurchaseStock>
    ) : RecyclerView.Adapter<Item1830RecyclerViewAdapter.ViewHolder>() {

        fun setData(newValues: List<PurchaseStock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fragment_1830_finish_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.tickerView.text = "${position}. ${item.stock.marketInstrument.ticker}"
            holder.priceView.text = item.stock.getPriceString()

            holder.purchaseLotsView.text = "${item.lots} шт."
            holder.purchasePriceView.text = item.getPriceString()
//            val volume = item.todayDayCandle.volume / 1000f;
//            holder.volumeTodayView.text = "%.1f".format(volume) + "k"
//
//            holder.changePriceAbsoluteView.text = "%.2f".format(item.changePriceDayAbsolute) + " $"
//            holder.changePricePercentView.text = "%.2f".format(item.changePriceDayPercent) + "%"
        }

        override fun getItemCount(): Int = values.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tickerView: TextView = view.findViewById(R.id.stock_item_ticker)
            val priceView: TextView = view.findViewById(R.id.stock_item_price)

            val purchaseLotsView: TextView = view.findViewById(R.id.stock_purchase_lots)
            val purchasePriceView: TextView = view.findViewById(R.id.stock_purchase_price)
        }
    }
}