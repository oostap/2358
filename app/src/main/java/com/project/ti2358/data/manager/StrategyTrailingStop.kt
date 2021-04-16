package com.project.ti2358.data.manager

import android.content.Intent
import com.project.ti2358.TheApplication
import com.project.ti2358.service.StrategyTrailingStopService
import com.project.ti2358.service.Utils
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent

@KoinApiExtension
class StrategyTrailingStop : KoinComponent {
    var activeTrailingStops: MutableList<TrailingStop> = mutableListOf()

    @Synchronized
    fun addTrailingStop(trailingStop: TrailingStop) {
        activeTrailingStops.add(trailingStop)

        if (!Utils.isServiceRunning(TheApplication.application.applicationContext, StrategyTrailingStopService::class.java)) {
            Utils.startService(TheApplication.application.applicationContext, StrategyTrailingStopService::class.java)
        }
    }

    @Synchronized
    fun removeTrailingStop(trailingStop: TrailingStop) {
        activeTrailingStops.remove(trailingStop)
        checkFinish()
    }

    fun stopTrailingStopsForStock(stock: Stock) {
        activeTrailingStops.removeAll { it.stock.ticker == stock.ticker }
        checkFinish()
    }

    fun stopStrategy() {
        activeTrailingStops.forEach { it.stop() }
        activeTrailingStops.clear()
        checkFinish()
    }

    private fun checkFinish() {
        if (activeTrailingStops.isEmpty()) {
            if (Utils.isServiceRunning(TheApplication.application.applicationContext, StrategyTrailingStopService::class.java)) {
                TheApplication.application.applicationContext.stopService(Intent(TheApplication.application.applicationContext, StrategyTrailingStopService::class.java))
            }
        }
    }

    fun getNotificationTitleShort(): String {
        return "Работает трейлинг стоп! 📈"
    }

    fun getNotificationTitleLong(): String {
        return "Бумаг: ${activeTrailingStops.size}"
    }

    fun getNotificationTextShort(): String {
        var info = ""
        for (stop in activeTrailingStops) {
            info += stop.getDescriptionShort()
        }

        return "${activeTrailingStops.size}:\n$info"
    }

    fun getNotificationTextLong(): String {
        activeTrailingStops.sortByDescending { it.currentChangePercent }

        var info = ""
        for (stop in activeTrailingStops) {
            info += stop.getDescriptionLong() + "\n"
        }

        return info
    }
}