package com.project.ti2358.data.manager

import com.project.ti2358.data.model.dto.Candle
import com.project.ti2358.service.Utils
import com.project.ti2358.service.log
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

@KoinApiExtension
class Strategy1728() : KoinComponent {
    private val stockManager: StockManager by inject()
    var stocks: MutableList<Stock> = mutableListOf()

    var stocks700_1200: MutableList<Stock> = mutableListOf()
    var stocks700_1600: MutableList<Stock> = mutableListOf()
    var stocks1630_1635: MutableList<Stock> = mutableListOf()
    var stocksFinal: MutableList<Stock> = mutableListOf()

    var time1630: Calendar
    var time1635: Calendar
    var time1645: Calendar

    init {
        val differenceHours: Int = Utils.getTimeDiffBetweenMSK()

        // 16:30:00.000
        time1630 = Calendar.getInstance(TimeZone.getDefault())
        time1630.apply {
            add(Calendar.HOUR_OF_DAY, -differenceHours)
            set(Calendar.HOUR_OF_DAY, 16)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.HOUR_OF_DAY, differenceHours)
        }

        // 16:35:00.000
        time1635 = Calendar.getInstance(TimeZone.getDefault())
        time1635.apply {
            add(Calendar.HOUR_OF_DAY, -differenceHours)
            set(Calendar.HOUR_OF_DAY, 16)
            set(Calendar.MINUTE, 35)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.HOUR_OF_DAY, differenceHours)
        }

        // 16:45:00.000
        time1645 = Calendar.getInstance(TimeZone.getDefault())
        time1645.apply {
            add(Calendar.HOUR_OF_DAY, -differenceHours)
            set(Calendar.HOUR_OF_DAY, 16)
            set(Calendar.MINUTE, 45)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.HOUR_OF_DAY, differenceHours)
        }
    }

    fun processFinal(): MutableList<Stock> {
        val candidates = process700to1200() + process700to1600()
        stocksFinal = process1630to1635()
        stocksFinal.removeAll { it !in candidates }
        stocksFinal.sortByDescending { it.changePrice1630to1635Percent }
        return stocksFinal
    }

    private fun process(): MutableList<Stock> {
        val all = stockManager.stocksStream
        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()
        stocks = all.filter { it.getPriceDouble() > min && it.getPriceDouble() < max }.toMutableList()
        return stocks
    }

    fun process700to1200(): MutableList<Stock> {
        stocks700_1200 = process()

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
                        stock.changePrice700to1200Absolute = from700to1200.closePrice - close.post
                        stock.changePrice700to1200Percent = (100 * from700to1200.closePrice) / close.post - 100
                        stock.volume700to1200 = from700to1200.volume
                    }
                }
            }
        }
        stocks700_1200.removeAll { it.volume700to1200 == 0 }
        stocks700_1200.removeAll { it.volume700to1200 < volume }
        stocks700_1200.removeAll { it.changePrice700to1200Percent < change}
        stocks700_1200.sortByDescending { it.changePrice700to1200Percent }

        return stocks700_1200
    }

    fun process700to1600(): MutableList<Stock> {
        stocks700_1600 = process()

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
                        stock.changePrice700to1600Absolute = from700to1600.closePrice - close.post
                        stock.changePrice700to1600Percent = (100 * from700to1600.closePrice) / close.post - 100
                        stock.volume700to1600 = from700to1600.volume
                    }
                }
            }
        }

        stocks700_1600.removeAll { it.volume700to1600 == 0 }
        stocks700_1600.removeAll { it.volume700to1600 < volume }
        stocks700_1600.removeAll { it.changePrice700to1600Percent < change}
        stocks700_1600.sortByDescending { it.changePrice700to1600Percent }

        return stocks700_1600
    }

    fun process1630to1635(): MutableList<Stock> {
        stocks1630_1635 = process()

        val volume = SettingsManager.get1728Volume(2)
        val change = SettingsManager.get1728ChangePercent()
        stocks1630_1635 = stocks1630_1635.filter { it.getTodayVolume() >= volume }.toMutableList()

        log("1728 FROM ${time1630.time}")
        log("1728 TO ${time1635.time}")

        val msk = Utils.getTimeMSK()
        if (msk.time >= time1645.time) {
            stocks700_1600.forEach { stock ->
                stock.volume1630to1635 = 0
                stock.changePrice1630to1635Absolute = 0.0
                stock.changePrice1630to1635Percent = 0.0

                stock.priceSteps1728?.let { close1728 ->
                    close1728.from1630to1635?.let { from1630to1635 ->
                        from1630to1635.openPrice?.let { open ->
                            stock.changePrice1630to1635Absolute = from1630to1635.closePrice - open
                            stock.changePrice1630to1635Percent = (100 * from1630to1635.closePrice) / open - 100
                            stock.volume1630to1635 = from1630to1635.volume
                        }
                    }
                }
            }
        } else {
            stocks1630_1635.forEach { stock ->
                val processingCandles = mutableListOf<Candle>()
                stock.volume1630to1635 = 0
                stock.changePrice1630to1635Absolute = 0.0
                stock.changePrice1630to1635Percent = 0.0
                synchronized(stock.minuteCandles) {
                    val candles = stock.minuteCandles
                    for (candle in candles) {
                        if (candle.time >= time1630.time && candle.time <= time1635.time) {
                            processingCandles.add(candle)
                            stock.volume1630to1635 += candle.volume
                        }
                    }
                }

                if (stock.volume1630to1635 < volume) processingCandles.clear()

                // вычисляем change
                if (processingCandles.isNotEmpty()) {
                    val first = processingCandles.first()
                    val last = processingCandles.last()
                    stock.changePrice1630to1635Absolute = last.closingPrice - first.openingPrice
                    stock.changePrice1630to1635Percent = (100 * last.closingPrice) / first.openingPrice - 100
                }
            }
        }

        stocks1630_1635.removeAll { it.changePrice1630to1635Percent < change }
        stocks1630_1635.sortByDescending { it.changePrice1630to1635Percent }

        return stocks1630_1635
    }
}