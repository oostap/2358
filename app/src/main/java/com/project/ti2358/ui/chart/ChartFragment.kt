package com.project.ti2358.ui.chart

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.CandleStickChart
import com.github.mikephil.charting.charts.Chart
import com.github.mikephil.charting.components.*
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.github.mikephil.charting.utils.MPPointF
import com.project.ti2358.R
import com.project.ti2358.data.manager.ChartManager
import com.project.ti2358.data.manager.DepositManager
import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.manager.StockManager
import com.project.ti2358.data.model.dto.Candle
import com.project.ti2358.data.model.dto.Interval
import com.project.ti2358.databinding.FragmentChartBinding
import com.project.ti2358.service.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.text.DecimalFormat
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
            return "%02d:%02d".format(timeCandle.get(Calendar.HOUR_OF_DAY), timeCandle.get(Calendar.MINUTE))
        }
    }
}

class VolumeFormatter : ValueFormatter() {
    var candles: MutableList<Candle> = mutableListOf()

    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
        return "%.1fk".format(locale = Locale.US, value / 1000f)
    }
}

class MyMarkerView(context: Context?, layoutResource: Int) : MarkerView(context, layoutResource) {
    private val tvContent: TextView = findViewById(R.id.tvContent)

    override fun refreshContent(e: Entry, highlight: Highlight) {
        if (e is CandleEntry) {
            val candle = e.data as Candle
            tvContent.text = ("v: %d\no: %.2f\nh: %.2f\nl: %.2f\nc: %.2f").format(candle.volume, e.open, e.high, e.low, e.close)
        }
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF((-(width / 2)).toFloat(), (-height).toFloat())
    }
}

class XYMarkerView(context: Context?, private val xAxisValueFormatter: IAxisValueFormatter) : MarkerView(context, R.layout.chart_marker) {
    private val tvContent: TextView
    private val format: DecimalFormat

    override fun refreshContent(e: Entry, highlight: Highlight) {
        tvContent.text = e.y.toInt().toString()
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF((-(width / 2)).toFloat(), (-height).toFloat())
    }

    init {
        tvContent = findViewById(R.id.tvContent)
        format = DecimalFormat("###.0")
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

@KoinApiExtension
class ChartFragment : Fragment(R.layout.fragment_chart), OnChartGestureListener {
    private val chartManager: ChartManager by inject()
    val depositManager: DepositManager by inject()
    val stockManager: StockManager by inject()

    private var fragmentChartBinding: FragmentChartBinding? = null

    var candleChartDateFormatter = DateFormatter()
    var volumeFormatter = VolumeFormatter()

    var activeStock: Stock? = null
    var currentInterval: Interval = Interval.FIVE_MINUTES
    var job: Job? = null

    override fun onDestroy() {
        job?.cancel()
        fragmentChartBinding = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentChartBinding.bind(view)
        fragmentChartBinding = binding

        binding.chartCandleView.onChartGestureListener = this
        binding.chartBarOsView.onChartGestureListener = this

        activeStock = chartManager.activeStock

        with(binding) {
            chartBarOsView.apply {
                setTouchEnabled(false)
                axisRight.isEnabled = false
                axisLeft.isEnabled = false
                description.isEnabled = false
                setDrawGridBackground(false)
                xAxis.isEnabled = false
                legend.form = Legend.LegendForm.NONE
            }

            chartCandleView.apply {
                description.isEnabled = false
                setBackgroundColor(Color.WHITE)
                setDrawGridBackground(true)

                isDragEnabled = true
                isLongClickable = true

                val mv = MyMarkerView(requireContext(), R.layout.chart_marker)
                mv.chartView = this
                marker = mv

                setBackgroundColor(Utils.EMPTY)
                setGridBackgroundColor(Utils.EMPTY)
                legend.form = Legend.LegendForm.NONE

                axisLeft.setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART)
                axisRight.setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART)
                axisRight.textColor = Utils.getChartTextColor()
                axisLeft.textColor = Utils.getChartTextColor()

                val xAxis: XAxis = xAxis
                xAxis.position = XAxis.XAxisPosition.TOP
                xAxis.axisMinimum = 0f
                xAxis.granularity = 1f
                xAxis.valueFormatter = candleChartDateFormatter
                xAxis.textColor = Utils.getChartTextColor()
            }

            chartBarView.apply {
                setDrawBarShadow(false)
                description.isEnabled = false
                isHighlightFullBarEnabled = false

                legend.form = Legend.LegendForm.NONE

                val xAxis = xAxis
                xAxis.position = XAxis.XAxisPosition.TOP
                xAxis.axisMinimum = 0f
                xAxis.granularity = 1f
                xAxis.valueFormatter = candleChartDateFormatter
                xAxis.textColor = Utils.getChartTextColor()

                axisRight.isEnabled = false
                axisLeft.setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART)
                axisLeft.axisMinimum = 0f
                axisLeft.granularity = 0f
                axisLeft.valueFormatter = volumeFormatter
                axisLeft.textColor = Utils.getChartTextColor()

                val barMarker = XYMarkerView(requireContext(), volumeFormatter)
                barMarker.chartView = this
                marker = barMarker
            }

            loadData(Interval.FIVE_MINUTES)

            min1Button.setOnClickListener {
                loadData(Interval.MINUTE)
            }

            min5Button.setOnClickListener {
                loadData(Interval.FIVE_MINUTES)
            }

            hour1Button.setOnClickListener {
                loadData(Interval.HOUR)
            }

            dayButton.setOnClickListener {
                loadData(Interval.DAY)
            }
        }
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

    private fun loadData(interval: Interval) {
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

    private fun loadData(candles: List<Candle>) {
        if (candles.isEmpty()) return

        if (currentInterval == Interval.MINUTE) {
            fragmentChartBinding?.chartBarOsView?.visibility = View.VISIBLE
        } else {
            fragmentChartBinding?.chartBarOsView?.visibility = View.GONE
        }

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
            bar.data = candle
            candleList.add(bar)
            i++
        }
        candleChartDateFormatter.candles = candles.toMutableList()
        volumeFormatter.candles = candles.toMutableList()

        val cds = CandleDataSet(candleList, "")
        cds.shadowColor = Utils.getChartTextColor()
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

        fragmentChartBinding?.chartCandleView?.data = candleData
        fragmentChartBinding?.chartCandleView?.invalidate()

        ////////?////////?////////?////////?////////?////////?////////?////////?////////?////////?////////?////////?
        val volumeList = mutableListOf<BarEntry>()
        val colorList = mutableListOf<Int>()
        i = 0
        for (candle in candles) {
            val bar = BarEntry(i.toFloat(), candle.volume.toFloat())
            val color = if (candle.openingPrice > candle.closingPrice) Utils.RED else Utils.GREEN
            volumeList.add(bar)
            colorList.add(color)
            i++
        }
        val set1 = BarDataSet(volumeList, "")
        set1.colors = colorList
        set1.valueTextColor = Color.rgb(60, 220, 78)
        set1.valueTextSize = 10f
        set1.setDrawValues(false)
        set1.axisDependency = YAxis.AxisDependency.LEFT

        val barData = BarData(set1)
        fragmentChartBinding?.chartBarView?.data = barData
        fragmentChartBinding?.chartBarView?.invalidate()

        // OS
        ////////?////////?////////?////////?////////?////////?////////?////////?////////?////////?////////?////////?
        .let {
            val volumeList = mutableListOf<BarEntry>()
            i = 0
            for (candle in candles) {
                val c = Utils.getTimeMSK()
                c.time = candle.time
                var volume = 0
                val hour = c.get(Calendar.HOUR_OF_DAY)
                val minutes = c.get(Calendar.MINUTE)
                if ((hour == 16 && minutes == 30) ||
                    (hour == 1 && minutes == 45) ||
                    (hour == 7 && minutes == 0) ||
                    (hour == 10 && minutes == 0) ||
                    (hour == 23 && minutes == 0)) {
                    volume = 1000000
                }

                val bar = BarEntry(i.toFloat(), volume.toFloat())
                volumeList.add(bar)
                i++
            }
            val set = BarDataSet(volumeList, "")
            set.color = Utils.PURPLE
            set.valueTextColor = Color.rgb(60, 220, 78)
            set.valueTextSize = 10f
            set.setDrawValues(false)

            val barData = BarData(set)
            fragmentChartBinding?.chartBarOsView?.data = barData
            fragmentChartBinding?.chartBarOsView?.invalidate()
        }
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
        fragmentChartBinding?.let {
            syncCharts(it.chartCandleView, listOf(it.chartBarView, it.chartBarOsView))
        }
    }

    override fun onChartSingleTapped(me: MotionEvent?) {
//        log("CHART onChartSingleTapped")
    }

    override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, velocityX: Float, velocityY: Float) {
//        log("CHART onChartFling")
    }

    override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {
//        log("CHART onChartScale")
        fragmentChartBinding?.let {
            syncCharts(it.chartCandleView, listOf(it.chartBarView, it.chartBarOsView))
        }
    }

    override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {
//        log("CHART onChartTranslate")
        fragmentChartBinding?.let {
            syncCharts(it.chartCandleView, listOf(it.chartBarView, it.chartBarOsView))
        }
    }
}

//RSI
//RS = Средняя цена Up Close / (Средняя цена Down Close) за данный период
//Фактическое значение RSI рассчитывается путем индексации индикатора до 100 с использованием следующей формулы:
//
//RSI = 100 - 100 / (1 + RS)