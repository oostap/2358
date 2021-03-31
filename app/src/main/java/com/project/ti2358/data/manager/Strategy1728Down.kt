package com.project.ti2358.data.manager

import com.project.ti2358.data.model.dto.Candle
import com.project.ti2358.service.Utils
import com.project.ti2358.service.log
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

@KoinApiExtension
class Strategy1728Down() : KoinComponent {
    private val stockManager: StockManager by inject()
    var stocks: MutableList<Stock> = mutableListOf()

    var stocks700_1200: MutableList<Stock> = mutableListOf()
    var stocks700_1600: MutableList<Stock> = mutableListOf()
    var stocks1625_1632: MutableList<Stock> = mutableListOf()
    var stocksFinal: MutableList<Stock> = mutableListOf()

    var time1625: Calendar
    var time1632: Calendar

    init {
        val differenceHours: Int = Utils.getTimeDiffBetweenMSK()

        // 16:28:00.000
        time1625 = Calendar.getInstance(TimeZone.getDefault())
        time1625.apply {
            add(Calendar.HOUR_OF_DAY, -differenceHours)
            set(Calendar.HOUR_OF_DAY, 16)
            set(Calendar.MINUTE, 25)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.HOUR_OF_DAY, differenceHours)
        }

        // 16:32:00.000
        time1632 = Calendar.getInstance(TimeZone.getDefault())
        time1632.apply {
            add(Calendar.HOUR_OF_DAY, -differenceHours)
            set(Calendar.HOUR_OF_DAY, 16)
            set(Calendar.MINUTE, 32)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.HOUR_OF_DAY, differenceHours)
        }
    }

    fun processFinal(): MutableList<Stock> {
        val candidates = process700to1600() //process700to1200() + process700to1600()
        stocksFinal = process1625to1632()
        stocksFinal.removeAll { it !in candidates }
        stocksFinal.sortByDescending { it.changePrice1630to1635Percent }
        return stocksFinal
    }

    private fun process(): MutableList<Stock> {
        val all = stockManager.getWhiteStocks()
        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()
        stocks = all.filter { it.getPriceNow() > min && it.getPriceNow() < max }.toMutableList()
        return stocks
    }

    fun process700to1200(): MutableList<Stock> {
        stocks700_1200 = process()

        val fromCloseOS = SettingsManager.get1728CalcFromOS()
        val change = SettingsManager.get1728ChangePercent()
        val volume = SettingsManager.get1728Volume(0)
        stocks700_1200 = stocks700_1200.filter { it.getTodayVolume() >= volume }.toMutableList()

        stocks700_1200.forEach { stock ->
            stock.volume700to1200 = 0
            stock.changePrice700to1200Absolute = 0.0
            stock.changePrice700to1200Percent = 0.0

            stock.closePrices?.let { close ->
                stock.priceSteps1728?.let { close1728 ->
                    close1728.from700to1200?.let { from700to1200 ->
                        val closePrice = if (fromCloseOS) close.os else close.post
                        stock.changePrice700to1200Absolute = from700to1200.closePrice - closePrice
                        stock.changePrice700to1200Percent = (100 * from700to1200.closePrice) / closePrice - 100
                        stock.volume700to1200 = from700to1200.volume
                    }
                }
            }
        }
        stocks700_1200.removeAll { it.volume700to1200 == 0 }
        stocks700_1200.removeAll { it.volume700to1200 < volume }
        stocks700_1200.removeAll { it.changePrice700to1200Percent > -change}
        stocks700_1200.sortBy { it.changePrice700to1200Percent }

        return stocks700_1200
    }

    fun process700to1600(): MutableList<Stock> {
        stocks700_1600 = process()

        val fromCloseOS = SettingsManager.get1728CalcFromOS()
        val change = SettingsManager.get1728ChangePercent()
        val volume = SettingsManager.get1728Volume(1)
        stocks700_1600 = stocks700_1600.filter { it.getTodayVolume() >= volume }.toMutableList()

        stocks700_1600.forEach { stock ->
            stock.volume700to1600 = 0
            stock.changePrice700to1600Absolute = 0.0
            stock.changePrice700to1600Percent = 0.0

            stock.closePrices?.let { close ->
                stock.priceSteps1728?.let { close1728 ->
                    close1728.from700to1600?.let { from700to1600 ->
                        val closePrice = if (fromCloseOS) close.os else close.post
                        stock.changePrice700to1600Absolute = from700to1600.closePrice - closePrice
                        stock.changePrice700to1600Percent = (100 * from700to1600.closePrice) / closePrice - 100
                        stock.volume700to1600 = from700to1600.volume
                    }
                }
            }
        }

        stocks700_1600.removeAll { it.volume700to1600 == 0 }
        stocks700_1600.removeAll { it.volume700to1600 < volume }
        stocks700_1600.removeAll { it.changePrice700to1600Percent > -change}
        stocks700_1600.sortBy { it.changePrice700to1600Percent }

        return stocks700_1600
    }

    fun process1625to1632(): MutableList<Stock> {
        stocks1625_1632 = process()

        log("1728 FROM ${time1625.time}")
        log("1728 TO ${time1632.time}")

        val volume = SettingsManager.get1728Volume(2)
        val change = SettingsManager.get1728ChangePercent()
        stocks1625_1632 = stocks1625_1632.filter { it.getTodayVolume() >= volume }.toMutableList()

        stocks1625_1632.forEach { stock ->
            stock.volume1625to1632 = 0
            stock.changePrice1625to1632Absolute = 0.0
            stock.changePrice1625to1632Percent = 0.0

//            if (stock.priceSteps1728?.from1630to1635 != null) { // на готовых данных
//                stock.priceSteps1728?.from1630to1635?.let { from1630to1635 ->
//                    from1630to1635.openPrice?.let { open ->
//                        stock.changePrice1630to1635Absolute = from1630to1635.closePrice - open
//                        stock.changePrice1630to1635Percent = (100 * from1630to1635.closePrice) / open - 100
//                        stock.volume1630to1635 = from1630to1635.volume
//                    }
//                }
//            } else {
                // на реалтайме с 1628 до 1632
                val processingCandles = mutableListOf<Candle>()
                synchronized(stock.minuteCandles) {
                    val candles = stock.minuteCandles
                    for (candle in candles) {
                        if (candle.time >= time1625.time && candle.time < time1632.time) {
                            processingCandles.add(candle)
                            stock.volume1625to1632 += candle.volume
                        }
                    }
                }

                // вычисляем change
                if (processingCandles.isNotEmpty()) {
                    val first = processingCandles.first()
                    val last = processingCandles.last()
                    stock.changePrice1625to1632Absolute = last.closingPrice - first.openingPrice
                    stock.changePrice1625to1632Percent = (100 * last.closingPrice) / first.openingPrice - 100
                }
//            }
        }

        stocks1625_1632.removeAll { it.volume1625to1632 == 0 }
        stocks1625_1632.removeAll { it.volume1625to1632 < volume }
        stocks1625_1632.removeAll { it.changePrice1625to1632Percent < change }
        stocks1625_1632.sortByDescending { it.changePrice1625to1632Percent }

        return stocks1625_1632
    }
}