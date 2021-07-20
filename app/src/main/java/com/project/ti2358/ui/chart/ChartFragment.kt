package com.project.ti2358.ui.chart

import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.icechao.klinelib.adapter.KLineChartAdapter
import com.icechao.klinelib.formatter.IDateTimeFormatter
import com.icechao.klinelib.utils.DateUtil
import com.icechao.klinelib.utils.LogUtil
import com.icechao.klinelib.utils.SlidListener
import com.icechao.klinelib.utils.Status
import com.project.ti2358.R
import com.project.ti2358.data.manager.ChartManager
import com.project.ti2358.data.manager.PortfolioTinkoffManager
import com.project.ti2358.data.manager.Stock
import com.project.ti2358.data.manager.StockManager
import com.project.ti2358.data.tinkoff.model.Candle
import com.project.ti2358.data.tinkoff.model.Interval
import com.project.ti2358.databinding.FragmentChartBinding
import com.project.ti2358.service.Utils
import com.project.ti2358.service.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.util.*


@KoinApiExtension
class ChartFragment : Fragment(R.layout.fragment_chart) {
    private val chartManager: ChartManager by inject()
    val portfolioTinkoffManager: PortfolioTinkoffManager by inject()
    val stockManager: StockManager by inject()

    private var fragmentChartBinding: FragmentChartBinding? = null

    var activeStock: Stock? = null
    var currentInterval: Interval = Interval.FIVE_MINUTES
    var job: Job? = null
    var jobRefreshMinutes: Job? = null
    var chartAdapter = KLineChartAdapter<Candle>()

    var currentIndex : Status.IndexStatus = Status.IndexStatus.NONE
    var currentCandles : List<Candle> = emptyList()

    override fun onDestroy() {
        job?.cancel()
        fragmentChartBinding = null
        super.onDestroy()
    }

    inner class DateFormatter : IDateTimeFormatter {
        override fun format(date: Date?): String? {
            return when (currentInterval) {
                in listOf(Interval.MINUTE, Interval.FIVE_MINUTES, Interval.TEN_MINUTES, Interval.FIFTEEN_MINUTES, Interval.THIRTY_MINUTES) -> {
                    DateUtil.HHMMTimeFormat.format(date)
                }
                Interval.HOUR -> DateUtil.MMddHHmmTimeFormat.format(date)
                else -> DateUtil.yyyyMMddFormat.format(date)

            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentChartBinding.bind(view)
        fragmentChartBinding = binding

        activeStock = chartManager.activeStock

        with(binding) {
            loadData(Interval.FIVE_MINUTES)

            min1Button.setOnClickListener {
                loadData(Interval.MINUTE)
            }

            min5Button.setOnClickListener {
                loadData(Interval.FIVE_MINUTES)
            }

            min10Button.setOnClickListener {
                loadData(Interval.TEN_MINUTES)
            }

            min15Button.setOnClickListener {
                loadData(Interval.FIFTEEN_MINUTES)
            }

            min30Button.setOnClickListener {
                loadData(Interval.THIRTY_MINUTES)
            }

            hour1Button.setOnClickListener {
                loadData(Interval.HOUR)
            }

            dayButton.setOnClickListener {
                loadData(Interval.DAY)
            }

            weekButton.setOnClickListener {
                loadData(Interval.WEEK)
            }

            volumeButton.setOnClickListener {
                chartLineView.volShowState = !chartLineView.volShowState
                updateVolumeButton()
            }

            maButton.setOnClickListener {
                if (chartLineView.status == Status.MainStatus.MA) {
                    chartLineView.changeMainDrawType(Status.MainStatus.NONE)
                } else {
                    chartLineView.changeMainDrawType(Status.MainStatus.MA)
                }
                updatePrimaryIndicatorButton()
            }

            bollButton.setOnClickListener {
                if (chartLineView.status == Status.MainStatus.BOLL) {
                    chartLineView.changeMainDrawType(Status.MainStatus.NONE)
                } else {
                    chartLineView.changeMainDrawType(Status.MainStatus.BOLL)
                }
                updatePrimaryIndicatorButton()
            }

            rsiButton.setOnClickListener {
                if (currentIndex == Status.IndexStatus.RSI) {
                    currentIndex = Status.IndexStatus.NONE
                } else {
                    currentIndex = Status.IndexStatus.RSI
                }

                chartLineView.setIndexDraw(currentIndex)
                updateSecondaryIndicatorButton()
            }

            macdButton.setOnClickListener {
                if (currentIndex == Status.IndexStatus.MACD) {
                    currentIndex = Status.IndexStatus.NONE
                } else {
                    currentIndex = Status.IndexStatus.MACD
                }

                chartLineView.setIndexDraw(currentIndex)
                updateSecondaryIndicatorButton()
            }

            kdjButton.setOnClickListener {
                if (currentIndex == Status.IndexStatus.KDJ) {
                    currentIndex = Status.IndexStatus.NONE
                } else {
                    currentIndex = Status.IndexStatus.KDJ
                }

                chartLineView.setIndexDraw(currentIndex)
                updateSecondaryIndicatorButton()
            }

            wrButton.setOnClickListener {
                if (currentIndex == Status.IndexStatus.WR) {
                    currentIndex = Status.IndexStatus.NONE
                } else {
                    currentIndex = Status.IndexStatus.WR
                }

                chartLineView.setIndexDraw(currentIndex)
                updateSecondaryIndicatorButton()
            }

            view.viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (view.height > 0 && view.width > 0) {
                        view.viewTreeObserver.removeOnGlobalLayoutListener(this)

                        val dp = (100 * Resources.getSystem().displayMetrics.density).toInt()
                        chartLineView.layoutParams = FrameLayout.LayoutParams(view.width, view.height - dp)
                    }
                }
            })

            chartAdapter = KLineChartAdapter()
            chartLineView.setAdapter(chartAdapter)
                .setSelectedPointRadius(20f)
                .setPriceLabelInLineMarginRight(200f)
                .setSelectedPointColor(Color.RED)
                .setIncreaseColor(Utils.GREEN)
                .setDecreaseColor(Utils.RED)
                .setAnimLoadData(false)
                .setPriceLabelInLineClickable(true)
                .setLabelSpace(130f)
                .setDateTimeFormatter(DateFormatter())
                .setGridColumns(5)
                .setGridRows(5)
                .setOverScrollRange((requireActivity().windowManager.defaultDisplay.width / 5).toFloat()) //滑动边界监听  Sliding boundary monitoring
                .setSlidListener(object : SlidListener {
                    override fun onSlidLeft() {
                        LogUtil.e("onSlidLeft")
                    }

                    override fun onSlidRight() {
                        LogUtil.e("onSlidRight")
                    }
                })
                .setOnSelectedChangedListener { view, index, values ->
                    log("CHANGE CLICK ${values[0]}")
                }

            val marketInfoText = arrayOf("time", "o", "h", "l", "c", "chg$", "chg%", "vol")
            chartLineView.setSelectedInfoLabels(marketInfoText)
            chartLineView.setIndexDraw(Status.IndexStatus.MACD)
            chartLineView.changeMainDrawType(Status.MainStatus.BOLL)
            chartLineView.setVolChartStatues(Status.VolChartStatus.BAR_CHART)
            chartLineView.volShowState = true
            chartLineView.setCrossFollowTouch(Status.CrossTouchModel.FOLLOW_FINGERS)
            chartLineView.setYLabelState(Status.YLabelModel.LABEL_WITH_GRID)

            currentIndex = Status.IndexStatus.MACD
            updateTitleData()
            updateVolumeButton()
            updatePrimaryIndicatorButton()
            updateSecondaryIndicatorButton()
        }
    }

    private fun loadData(interval: Interval) {
        currentInterval = interval

        job?.cancel()
        job = GlobalScope.launch(Dispatchers.Main) {
            try {
                currentCandles = chartManager.loadCandlesForInterval(activeStock, currentInterval)
                loadData(currentCandles)

//                jobRefreshMinutes?.cancel()
//
//                if (currentInterval == Interval.MINUTE) {
//                    jobRefreshMinutes = GlobalScope.launch(StockManager.stockContext) {
//                        while (true) {
//                            delay(500)
//
//                            var i = 0
//                            activeStock?.minuteCandles?.forEach { stockCandle ->
//                                log("START !! ${stockCandle.time.time}")
//
//                                i = 0
//                                currentCandles.forEach { chartCandle ->
//                                    log("END !! ${chartCandle.time.time}")
//                                    if (chartCandle.time.time == stockCandle.time.time && chartCandle.volume != stockCandle.volume) {
//                                        chartAdapter.changeItem(i, stockCandle)
//                                    } else if (stockCandle.time.time > chartCandle.time.time) {
//                                        chartAdapter.addLast(stockCandle)
//                                    }
//                                    i++
//                                }
//                            }
//                        }
//                    }
//                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        updateIntervalButtons()
    }

    private fun updateSecondaryIndicatorButton() {
        fragmentChartBinding?.apply {
            val colorDefault = Utils.DARK_BLUE
            val colorSelect = Utils.RED

            macdButton.setBackgroundColor(colorDefault)
            rsiButton.setBackgroundColor(colorDefault)
            kdjButton.setBackgroundColor(colorDefault)
            wrButton.setBackgroundColor(colorDefault)

            if (currentIndex == Status.IndexStatus.MACD) {
                macdButton.setBackgroundColor(colorSelect)
            } else if (currentIndex == Status.IndexStatus.RSI) {
                rsiButton.setBackgroundColor(colorSelect)
            } else if (currentIndex == Status.IndexStatus.KDJ) {
                kdjButton.setBackgroundColor(colorSelect)
            } else if (currentIndex == Status.IndexStatus.WR) {
                wrButton.setBackgroundColor(colorSelect)
            }
        }
    }

    private fun updatePrimaryIndicatorButton() {
        fragmentChartBinding?.apply {
            val colorDefault = Utils.DARK_BLUE
            val colorSelect = Utils.RED

            bollButton.setBackgroundColor(colorDefault)
            maButton.setBackgroundColor(colorDefault)

            if (chartLineView.status == Status.MainStatus.BOLL) {
                bollButton.setBackgroundColor(colorSelect)
            } else if (chartLineView.status == Status.MainStatus.MA) {
                maButton.setBackgroundColor(colorSelect)
            }
        }
    }

    private fun updateVolumeButton() {
        fragmentChartBinding?.apply {
            val colorDefault = Utils.DARK_BLUE
            val colorSelect = Utils.RED

            if (chartLineView.volShowState) {
                volumeButton.setBackgroundColor(colorSelect)
            } else {
                volumeButton.setBackgroundColor(colorDefault)
            }
        }
    }

    private fun updateIntervalButtons() {
        fragmentChartBinding?.apply {
            val colorDefault = Utils.DARK_BLUE
            val colorSelect = Utils.RED

            min1Button.setBackgroundColor(colorDefault)
            min5Button.setBackgroundColor(colorDefault)
            min10Button.setBackgroundColor(colorDefault)
            min15Button.setBackgroundColor(colorDefault)
            min30Button.setBackgroundColor(colorDefault)
            hour1Button.setBackgroundColor(colorDefault)
            weekButton.setBackgroundColor(colorDefault)
            dayButton.setBackgroundColor(colorDefault)

            when (currentInterval) {
                Interval.MINUTE -> min1Button.setBackgroundColor(colorSelect)
                Interval.FIVE_MINUTES -> min5Button.setBackgroundColor(colorSelect)
                Interval.TWO_MINUTES -> TODO()
                Interval.THREE_MINUTES -> TODO()
                Interval.TEN_MINUTES -> min10Button.setBackgroundColor(colorSelect)
                Interval.FIFTEEN_MINUTES -> min15Button.setBackgroundColor(colorSelect)
                Interval.THIRTY_MINUTES -> min30Button.setBackgroundColor(colorSelect)
                Interval.HOUR -> hour1Button.setBackgroundColor(colorSelect)
                Interval.TWO_HOURS -> TODO()
                Interval.FOUR_HOURS -> TODO()
                Interval.DAY -> dayButton.setBackgroundColor(colorSelect)
                Interval.WEEK -> weekButton.setBackgroundColor(colorSelect)
                Interval.MONTH -> TODO()
            }
        }
    }

    private fun updateTitleData() {
        val ticker = activeStock?.instrument?.ticker ?: ""
        val act = requireActivity() as AppCompatActivity
        act.supportActionBar?.title = "$ticker"
    }

    private fun loadData(candles: List<Candle>) {
        if (candles.isEmpty()) return

        chartAdapter.resetData(candles, true)
    }
}