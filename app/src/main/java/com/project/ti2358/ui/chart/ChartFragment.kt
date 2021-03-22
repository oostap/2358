package com.project.ti2358.ui.chart

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.firebase.installations.Utils
import com.project.ti2358.R
import com.project.ti2358.data.manager.ChartManager
import com.project.ti2358.data.manager.DepositManager
import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.manager.StockManager
import com.project.ti2358.data.model.dto.Candle
import com.project.ti2358.data.model.dto.Interval
import com.project.ti2358.service.Utils.Companion.PURPLE
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi
import com.tradingview.lightweightcharts.api.options.models.layoutOptions
import com.tradingview.lightweightcharts.api.options.models.localizationOptions
import com.tradingview.lightweightcharts.api.series.models.BarData
import com.tradingview.lightweightcharts.api.series.models.HistogramData
import com.tradingview.lightweightcharts.api.series.models.Time
import com.tradingview.lightweightcharts.runtime.plugins.DateTimeFormat
import com.tradingview.lightweightcharts.runtime.plugins.PriceFormatter
import com.tradingview.lightweightcharts.runtime.plugins.TimeFormatter
import com.tradingview.lightweightcharts.view.ChartsView
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class ChartFragment : Fragment() {
    val chartManager: ChartManager by inject()
    val depositManager: DepositManager by inject()
    val stockManager: StockManager by inject()

    lateinit var chartView: ChartsView
    lateinit var candleSeries: SeriesApi
    lateinit var volumeSeries: SeriesApi

    var activeStock: Stock? = null
    var currentInterval: Interval = Interval.MINUTE

    var job: Job? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_chart, container, false)

        chartView = view.findViewById(R.id.chart_view)
        chartView.api.applyOptions {
            layout = layoutOptions {
                backgroundColor = Color.LTGRAY
                textColor = Color.BLACK
            }
            localization = localizationOptions {
                locale = "ru-RU"
                priceFormatter = PriceFormatter(template = "{price:#2:#3}$")
                timeFormatter = TimeFormatter(
                    locale = "ru-RU",
                    dateTimeFormat = DateTimeFormat.DATE_TIME
                )
            }
        }

        chartView.api.addCandlestickSeries { series ->
            candleSeries = series

            chartView.api.addHistogramSeries { seriesv ->
                volumeSeries = seriesv
                loadData(Interval.FIVE_MINUTES)
            }
        }

        activeStock = chartManager.activeStock

        val button1Min = view.findViewById<Button>(R.id.button_1min)
        button1Min.setOnClickListener {
            loadData(Interval.MINUTE)
        }

        val button5Min = view.findViewById<Button>(R.id.button_5min)
        button5Min.setOnClickListener {
            loadData(Interval.FIVE_MINUTES)
        }

        val button1Hour = view.findViewById<Button>(R.id.button_1hour)
        button1Hour.setOnClickListener {
            loadData(Interval.HOUR)
        }

        val buttonDay = view.findViewById<Button>(R.id.button_day)
        buttonDay.setOnClickListener {
            loadData(Interval.DAY)
        }
        return view
    }

    fun loadData(interval: Interval) {
        currentInterval = interval

        job?.cancel()
        job = GlobalScope.launch(Dispatchers.Main) {
            when (currentInterval) {
                Interval.MINUTE -> loadData(chartManager.loadCandlesMinute1())
                Interval.FIVE_MINUTES -> loadData(chartManager.loadCandlesMinute5())
                Interval.HOUR -> loadData(chartManager.loadCandlesHour1())
                Interval.DAY -> loadData(chartManager.loadCandlesDay())
            }
        }
        updateTitleData()
    }

    private fun updateTitleData() {
        val ticker = activeStock?.instrument?.ticker ?: ""
        val act = requireActivity() as AppCompatActivity

        val min = when (currentInterval) {
            Interval.MINUTE -> "1 MIN"
            Interval.FIVE_MINUTES -> "5 MIN"
            Interval.HOUR -> "1 HOUR"
            Interval.DAY -> "DAY"
            else -> "NONE"
        }

        act.supportActionBar?.title = getString(R.string.menu_chart) + " $ticker" + " $min"
    }

    fun loadData(candles: List<Candle>) {
        val candleData = mutableListOf<BarData>()
        for (candle in candles) {
            val bar = BarData(Time.Utc.fromDate(candle.time), candle.openingPrice.toFloat(), candle.highestPrice.toFloat(), candle.lowestPrice.toFloat(), candle.closingPrice.toFloat())
            candleData.add(bar)
        }
        candleSeries.setData(candleData)

//        val volumeData = mutableListOf<HistogramData>()
//        for (candle in candles) {
//            val bar = HistogramData(Time.Utc.fromDate(candle.time), candle.volume / 1000f)
//            volumeData.add(bar)
//        }
//        volumeSeries.setData(volumeData)
    }
}