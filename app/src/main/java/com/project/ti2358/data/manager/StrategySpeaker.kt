package com.project.ti2358.data.manager

import android.os.Build
import android.speech.tts.TextToSpeech
import com.project.ti2358.TheApplication
import com.project.ti2358.service.LimitType
import com.project.ti2358.service.Utils
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

@KoinApiExtension
class StrategySpeaker : KoinComponent, TextToSpeech.OnInitListener {
    private var textToSpeech: TextToSpeech? = null

    fun start() {
        if (textToSpeech == null) {
            textToSpeech = TextToSpeech(TheApplication.application.applicationContext, this)
        }
    }

    override fun onInit(i: Int) {
        if (i == TextToSpeech.SUCCESS) {
            textToSpeech?.let {
                val locale = Locale.getDefault()
                val result = it.setLanguage(locale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Utils.showToastAlert("Голосовой движок не пашет!")
                }
            }
        }
    }

    private fun speak(text: String) {
        textToSpeech?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                it.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            } else {
                it.speak(text, TextToSpeech.QUEUE_FLUSH, null)
            }
        }
    }

    fun speakRocket(stockRocket: StockRocket) {
        if (SettingsManager.getRocketVoice()) {
            val text = makeNiceChange(stockRocket.stock.ticker, stockRocket.changePercent)
            val type = if (stockRocket.changePercent > 0) "ракета" else "комета"
            speak("$type $text ${stockRocket.time} мин")
        }
    }

    fun speakTazik(stockPurchase: StockPurchase, change: Double) {
        if (SettingsManager.getTazikVoice()) {
            val text = makeNiceChange(stockPurchase.stock.ticker, change)
            speak(text)
        }
    }

    fun speakZontik(stockPurchase: StockPurchase, change: Double) {
        if (SettingsManager.getTazikEndlessVoice()) {
            val text = makeNiceChange(stockPurchase.stock.ticker, change)
            speak("шорт. $text")
        }
    }

    fun speakTazikSpikeSkip(stockPurchase: StockPurchase, change: Double) {
        if (SettingsManager.getTazikVoice() && SettingsManager.getTelegramSendSpikes()) {
            val text = makeNiceChange(stockPurchase.stock.ticker, change)
            speak("спайк. $text")
        }
    }

    fun speakZontikSpikeSkip(stockPurchase: StockPurchase, change: Double) {
        if (SettingsManager.getTazikEndlessVoice() && SettingsManager.getTelegramSendSpikes()) {
            val text = makeNiceChange(stockPurchase.stock.ticker, change)
            speak("спайк. шорт. $text")
        }
    }

    fun speakTrend(stockTrend: StockTrend) {
        if (SettingsManager.getTrendVoice()) {
            val text = makeNiceChange(stockTrend.stock.ticker, stockTrend.changeFromStartToLow)
            val type = if (stockTrend.changeFromStartToLow < 0) "Отскок вверх️" else "Отскок вниз"
            speak("$type $text") // ${trendStock.timeFromStartToLow} -> ${trendStock.timeFromStartToLow} мин -> ")
        }
    }

    fun speakLimit(stockLimit: StockLimit) {
        if (SettingsManager.getLimitsVoice()) {
            val tickerLetters = stockLimit.stock.ticker.split("")
            var text = tickerLetters.joinToString(" ")
            text += when (stockLimit.type)  {
                LimitType.ON_UP -> " на лимите"
                LimitType.ON_DOWN -> " на лимите"

                LimitType.ABOVE_UP -> " выше лимита"
                LimitType.UNDER_DOWN -> "️️ ниже лимита"

                LimitType.NEAR_UP -> " рядом с лимитом"
                LimitType.NEAR_DOWN -> "️️ рядом с лимитом"
            }
            speak(text)
        }
    }

    private fun makeNiceChange(ticker: String, change: Double) : String {
        val tickerLetters = ticker.split("")
        val tickerSpaced = tickerLetters.joinToString(" ")

        val first = change.toInt()
        val second = abs((change - first) * 10).roundToInt()

        var text = tickerSpaced
        if (change > 0) text += " +"
        text += " $first"
        if (second != 0) text += " и $second"
        text += "%"

        return text
    }

    fun test() {
        val text = makeNiceChange("CCXI", -50.4)
        speak("спайк. $text")
    }
}