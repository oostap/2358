package com.project.ti2358.ui.chart

import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.CandleStickChart
import com.github.mikephil.charting.charts.Chart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.project.ti2358.R
import com.project.ti2358.data.manager.ChartManager
import com.project.ti2358.data.manager.DepositManager
import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.manager.StockManager
import com.project.ti2358.data.model.dto.Candle
import com.project.ti2358.data.model.dto.Interval
import com.project.ti2358.service.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.util.*

class DateFormatter : ValueFormatter() {
    var candles: MutableList<Candle> = mutableListOf()

    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
        val index = value.toInt()
        if (index >= candles.size) return ""

        val candle = candles[value.toInt()]
        val timeCandle = Calendar.getInstance()
        timeCandle.time = candle.time

        if (candle.interval == Interval.DAY) {
            return "%02d.%02d".format(timeCandle.get(Calendar.MONTH), timeCandle.get(Calendar.DAY_OF_MONTH))
        } else {
            return "%02d:%02d".format(timeCandle.get(Calendar.HOUR), timeCandle.get(Calendar.MINUTE))
        }
    }
}

class VolumeFormatter : ValueFormatter() {
    var candles: MutableList<Candle> = mutableListOf()

    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
        return "%.1fk".format(value / 1000f)
    }
}

@KoinApiExtension
class ChartFragment : Fragment(), OnChartGestureListener {
    private val chartManager: ChartManager by inject()
    val depositManager: DepositManager by inject()
    val stockManager: StockManager by inject()

    var candleChartDateFormatter = DateFormatter()
    var volumeFormatter = VolumeFormatter()

    lateinit var candleChartView: CandleStickChart
    lateinit var barChartView: BarChart

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

        candleChartView = view.findViewById(R.id.chart_candle_view)
        candleChartView.description.isEnabled = false
        candleChartView.setBackgroundColor(Color.WHITE)
        candleChartView.setDrawGridBackground(true)

        barChartView = view.findViewById(R.id.chart_bar_view)
        barChartView.setDrawBarShadow(false)
        barChartView.description.isEnabled = false
        barChartView.isHighlightFullBarEnabled = false

        candleChartView.setBackgroundColor(Utils.EMPTY)
        candleChartView.setGridBackgroundColor(Utils.EMPTY)

        candleChartView.legend.form = Legend.LegendForm.NONE
        barChartView.legend.form = Legend.LegendForm.NONE

        candleChartView.onChartGestureListener = this
        barChartView.onChartGestureListener = this

//        candleChartView.axisLeft.setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART)
        candleChartView.axisRight.setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART)
        candleChartView.axisRight.textColor = Utils.getChartTextColor()
        candleChartView.axisLeft.textColor = Utils.getChartTextColor()

        var xAxis: XAxis = candleChartView.xAxis
        xAxis.position = XAxis.XAxisPosition.TOP
        xAxis.axisMinimum = 0f
        xAxis.granularity = 1f
        xAxis.valueFormatter = candleChartDateFormatter
        xAxis.textColor = Utils.getChartTextColor()

        xAxis = barChartView.xAxis
        xAxis.position = XAxis.XAxisPosition.TOP
        xAxis.axisMinimum = 0f
        xAxis.granularity = 1f
        xAxis.valueFormatter = candleChartDateFormatter
        xAxis.textColor = Utils.getChartTextColor()

        barChartView.axisRight.isEnabled = false

        barChartView.axisLeft.axisMinimum = 0f
        barChartView.axisLeft.granularity = 0f
        barChartView.axisLeft.valueFormatter = volumeFormatter
        barChartView.axisLeft.textColor = Utils.getChartTextColor()

        activeStock = chartManager.activeStock

        loadData(Interval.FIVE_MINUTES)

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

    private fun syncCharts(mainChart: Chart<*>, otherCharts: List<Chart<*>>) {
        val mainVals = FloatArray(9)
        var otherMatrix: Matrix
        val otherVals = FloatArray(9)
        val mainMatrix: Matrix = mainChart.viewPortHandler.matrixTouch
        mainMatrix.getValues(mainVals)
        for (tempChart in otherCharts) {
            otherMatrix = tempChart.viewPortHandler.matrixTouch
            otherMatrix.getValues(otherVals)
            otherVals[Matrix.MSCALE_X] = mainVals[Matrix.MSCALE_X]
            otherVals[Matrix.MTRANS_X] = mainVals[Matrix.MTRANS_X]
            otherVals[Matrix.MSKEW_X] = mainVals[Matrix.MSKEW_X]
            otherMatrix.setValues(otherVals)
            tempChart.viewPortHandler.refresh(otherMatrix, tempChart, true)
        }
    }

    fun loadData(interval: Interval) {
        currentInterval = interval

        job?.cancel()
        job = GlobalScope.launch(Dispatchers.Main) {
            try {
                when (currentInterval) {
                    Interval.MINUTE -> loadData(chartManager.loadCandlesMinute1())
                    Interval.FIVE_MINUTES -> loadData(chartManager.loadCandlesMinute5())
                    Interval.HOUR -> loadData(chartManager.loadCandlesHour1())
                    Interval.DAY -> loadData(chartManager.loadCandlesDay())
                }
            } catch (e: Exception) {
                e.printStackTrace()
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

        act.supportActionBar?.title = "$ticker $min"
    }

    fun loadData(candles: List<Candle>) {
        val candleList = mutableListOf<CandleEntry>()
        var i = 0
        for (candle in candles) {
            val bar = CandleEntry(
                i.toFloat(),
                candle.highestPrice.toFloat(),
                candle.lowestPrice.toFloat(),
                candle.openingPrice.toFloat(),
                candle.closingPrice.toFloat()
            )
            candleList.add(bar)
            i++
        }
        candleChartDateFormatter.candles = candles.toMutableList()
        volumeFormatter.candles = candles.toMutableList()

        val cds = CandleDataSet(candleList, "")
//        cds.color = Color.rgb(150, 150, 150)
//        cds.shadowColor = Color.DKGRAY
        cds.shadowWidth = 0.7f
        cds.decreasingColor = Utils.RED
        cds.decreasingPaintStyle = Paint.Style.FILL
        cds.increasingColor = Utils.GREEN
        cds.increasingPaintStyle = Paint.Style.FILL
        cds.neutralColor = Utils.getNeutralColor()
        cds.valueTextColor = Utils.RED
        cds.valueTextSize = 10f
        cds.setDrawValues(false)

        val candleData = CandleData()
        candleData.addDataSet(cds)
        candleChartView.data = candleData
        candleChartView.invalidate()
        ////////?////////?////////?////////?////////?////////?////////?////////?////////?////////?////////?////////?
        val volumeList = mutableListOf<BarEntry>()
        i = 0
        for (candle in candles) {
            val bar = BarEntry(i.toFloat(), candle.volume.toFloat())
            volumeList.add(bar)
            i++
        }
        val set1 = BarDataSet(volumeList, "")
        set1.color = Color.rgb(60, 220, 78)
        set1.valueTextColor = Color.rgb(60, 220, 78)
        set1.valueTextSize = 10f
        set1.setDrawValues(false)
        set1.axisDependency = YAxis.AxisDependency.LEFT

        val barData = BarData(set1)
        barChartView.data = barData
        barChartView.invalidate()
    }

    override fun onChartGestureStart(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {
//        log("CHART onChartGestureStart")
    }

    override fun onChartGestureEnd(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {
//        log("CHART onChartGestureEnd")
    }

    override fun onChartLongPressed(me: MotionEvent?) {
//        log("CHART onChartLongPressed")
    }

    override fun onChartDoubleTapped(me: MotionEvent?) {
//        log("CHART onChartDoubleTapped")
        syncCharts(candleChartView, listOf(barChartView))
    }

    override fun onChartSingleTapped(me: MotionEvent?) {
//        log("CHART onChartSingleTapped")
    }

    override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, velocityX: Float, velocityY: Float) {
//        log("CHART onChartFling")
    }

    override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {
//        log("CHART onChartScale")
        syncCharts(candleChartView, listOf(barChartView))
    }

    override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {
//        log("CHART onChartTranslate")
        syncCharts(candleChartView, listOf(barChartView))
    }
}

//RSI
//RS = Средняя цена Up Close / (Средняя цена Down Close) за данный период
//Фактическое значение RSI рассчитывается путем индексации индикатора до 100 с использованием следующей формулы:
//
//RSI = 100 - 100 / (1 + RS)