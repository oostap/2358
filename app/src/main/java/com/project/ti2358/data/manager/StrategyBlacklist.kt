package com.project.ti2358.data.manager

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.project.ti2358.R
import com.project.ti2358.TheApplication
import com.project.ti2358.service.*
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent

@KoinApiExtension
class StrategyBlacklist : KoinComponent {
    var stocks: MutableList<Stock> = mutableListOf()
    var currentSort: Sorting = Sorting.DESCENDING

    companion object {
        var stocksSelected: MutableList<Stock> = mutableListOf()
    }

    suspend fun process(allStocks: List<Stock>): MutableList<Stock> = withContext(StockManager.stockContext) {
        stocks = allStocks.toMutableList()
        stocks.sortBy { it.changePrice2300DayPercent }
        loadSelectedStocks()
        return@withContext stocks
    }

    fun getBlacklistStocks(): MutableList<Stock> {
        return stocksSelected
    }
    private fun loadSelectedStocks() {
        stocksSelected.clear()

        val stocksSelectedList: List<String> = SettingsManager.getBlackSet()
        stocksSelected = stocks.filter { it.ticker in stocksSelectedList }.toMutableList()
    }

    private fun saveSelectedStocks() {
        val setList = stocksSelected.map { it.ticker }.toMutableList()

        val preferences = PreferenceManager.getDefaultSharedPreferences(TheApplication.application.applicationContext)
        val editor: SharedPreferences.Editor = preferences.edit()
        val key = TheApplication.application.applicationContext.getString(R.string.setting_key_black_set)
        editor.putString(key, setList.joinToString(separator = " "))
        editor.apply()
    }

    suspend fun resort(): MutableList<Stock> = withContext(StockManager.stockContext) {
        currentSort = if (currentSort == Sorting.DESCENDING) Sorting.ASCENDING else Sorting.DESCENDING
        stocks.sortBy {
            val sign = if (currentSort == Sorting.ASCENDING) 1 else -1
            val multiplier = if (it in stocksSelected) 100 else 1
            it.changePrice2300DayPercent * sign - multiplier
        }
        return@withContext stocks
    }

    fun setSelected(stock: Stock, value: Boolean) {
        if (value) {
            if (stock !in stocksSelected)
                stocksSelected.add(stock)
        } else {
            stocksSelected.remove(stock)
        }
        stocksSelected.sortBy { it.changePrice2300DayPercent }

        saveSelectedStocks()
    }

    fun isSelected(stock: Stock): Boolean {
        return stock in stocksSelected
    }
}