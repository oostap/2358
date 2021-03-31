package com.project.ti2358.ui.strategy1728Down

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.ti2358.R
import com.project.ti2358.data.manager.*
import com.project.ti2358.databinding.Fragment1728DownBinding
import com.project.ti2358.databinding.Fragment1728ItemDownBinding
import com.project.ti2358.service.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.util.*
import kotlin.math.roundToInt

@KoinApiExtension
class Strategy1728DownFragment : Fragment(R.layout.fragment_1728_down) {
    val stockManager: StockManager by inject()
    val strategy1728Down: Strategy1728Down by inject()

    private var fragment1728DownBinding: Fragment1728DownBinding? = null

    var adapterList: Item1728DownRecyclerViewAdapter = Item1728DownRecyclerViewAdapter(emptyList())
    var step1728: Step1728 = Step1728.step700to1200
    var stocks: MutableList<Stock> = mutableListOf()
    var job: Job? = null

    override fun onDestroy() {
        job?.cancel()
        fragment1728DownBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = Fragment1728DownBinding.bind(view)
        fragment1728DownBinding = binding

        binding.list.addItemDecoration(DividerItemDecoration(binding.list.context, DividerItemDecoration.VERTICAL))
        binding.list.layoutManager = LinearLayoutManager(context)
        binding.list.adapter = adapterList

        binding.step1Button.setOnClickListener {
            updateData(Step1728.step700to1200)
        }

        binding.step2Button.setOnClickListener {
            updateData(Step1728.step700to1530)
        }

        binding.step3Button.setOnClickListener {
            updateData(Step1728.step1630to1635)
        }

        binding.stepFinalButton.setOnClickListener {
            updateData(Step1728.stepFinal)
        }

        job?.cancel()
        job = GlobalScope.launch(Dispatchers.Main) {
            stockManager.reloadStockPrice1728()
            updateData(step1728)
        }

        updateData(Step1728.step700to1200)
    }

    private fun updateData(localStep1728: Step1728) {
        step1728 = localStep1728
        stocks = when (step1728) {
            Step1728.step700to1200 -> strategy1728Down.process700to1200()
            Step1728.step700to1530 -> strategy1728Down.process700to1600()
            Step1728.step1630to1635 -> strategy1728Down.process1625to1632()
            Step1728.stepFinal -> strategy1728Down.processFinal()
        }
        adapterList.setData(stocks)
        updateTitle()
    }

    private fun updateTitle() {
        if (isAdded) {
            val title = when (step1728) {
                Step1728.step700to1200 -> "1: 07:00 - 12:00, объём ${SettingsManager.get1728Volume(0)}"
                Step1728.step700to1530 -> "2: 07:00 - 16:00, объём ${SettingsManager.get1728Volume(1)}"
                Step1728.step1630to1635 -> "3: 16:25 - 16:32, объём ${SettingsManager.get1728Volume(2)}"
                Step1728.stepFinal -> "1 - 2 - 3 - 🚀"
            }

            val act = requireActivity() as AppCompatActivity
            act.supportActionBar?.title = title
        }
    }

    inner class Item1728DownRecyclerViewAdapter(private var values: List<Stock>) : RecyclerView.Adapter<Item1728DownRecyclerViewAdapter.ViewHolder>() {
        fun setData(newValues: List<Stock>) {
            values = newValues
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(Fragment1728ItemDownBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)
        override fun getItemCount(): Int = values.size

        inner class ViewHolder(private val binding: Fragment1728ItemDownBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(index: Int) {
                val stock = values[index]

                with(binding) {
                    tickerView.text = "${index + 1}) ${stock.getTickerLove()}"
                    priceView.text = stock.getPriceNow().toMoney(stock)

                    val changePercent = when (step1728) {
                        Step1728.step700to1200 -> stock.changePrice700to1200Percent
                        Step1728.step700to1530 -> stock.changePrice700to1600Percent
                        Step1728.step1630to1635 -> stock.changePrice1625to1632Percent
                        Step1728.stepFinal -> stock.changePrice1625to1632Percent
                    }

                    val changeAbsolute = when (step1728) {
                        Step1728.step700to1200 -> stock.changePrice700to1200Absolute
                        Step1728.step700to1530 -> stock.changePrice700to1600Absolute
                        Step1728.step1630to1635 -> stock.changePrice1625to1632Absolute
                        Step1728.stepFinal -> stock.changePrice1625to1632Absolute
                    }

                    val volume = when (step1728) {
                        Step1728.step700to1200 -> stock.volume700to1200
                        Step1728.step700to1530 -> stock.volume700to1600
                        Step1728.step1630to1635 -> stock.volume1625to1632
                        Step1728.stepFinal -> stock.getTodayVolume()
                    } / 1000f

                    volumeSharesView.text = "%.1fk".format(locale = Locale.US, volume)

                    priceChangeAbsoluteView.text = changeAbsolute.toMoney(stock)
                    priceChangePercentView.text = changePercent.toPercent()

                    priceChangeAbsoluteView.setTextColor(Utils.getColorForValue(changePercent))
                    priceChangePercentView.setTextColor(Utils.getColorForValue(changePercent))

                    itemView.setOnClickListener {
                        Utils.openTinkoffForTicker(requireContext(), stock.ticker)
                    }

                    buyButton.setOnClickListener {
                        if (SettingsManager.get1728PurchaseVolume() <= 0) {
                            Utils.showMessageAlert(requireContext(), "В настройках не задана сумма покупки для позиции, раздел 1728.")
                        } else {
                            val purchase = PurchaseStock(stock)

                            // считаем лоты
                            purchase.lots = (SettingsManager.get1728PurchaseVolume() / purchase.stock.getPriceNow()).roundToInt()

                            // включаем трейлинг тейк
                            if (SettingsManager.get1728TrailingStop()) {
                                purchase.trailingStop = true
                                purchase.trailingStopTakeProfitPercentActivation = SettingsManager.getTrailingStopTakeProfitPercentActivation()
                                purchase.trailingStopTakeProfitPercentDelta = SettingsManager.getTrailingStopTakeProfitPercentDelta()
                                purchase.trailingStopStopLossPercent = SettingsManager.getTrailingStopStopLossPercent()
                            }

                            purchase.buyFromAsk1728()
                        }
                    }

                    if (step1728 == Step1728.stepFinal || step1728 == Step1728.step1630to1635) {
                        buyButton.visibility = View.VISIBLE
                    } else {
                        buyButton.visibility = View.GONE
                    }
                    itemView.setBackgroundColor(Utils.getColorForIndex(index))
                }
            }
        }
    }
}