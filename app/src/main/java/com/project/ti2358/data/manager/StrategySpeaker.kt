package com.project.ti2358.data.manager

import android.os.Build
import android.speech.tts.TextToSpeech
import com.project.ti2358.TheApplication
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

    fun speakRocket(rocketStock: RocketStock) {
        if (SettingsManager.getRocketVoice()) {
            val text = makeNiceChange(rocketStock.stock.ticker, rocketStock.changePercent)
            val type = if (rocketStock.changePercent > 0) "ракета" else "комета"
            speak("$type $text ${rocketStock.time} мин")
        }
    }

    fun speakTazik(purchaseStock: PurchaseStock, change: Double) {
        if (SettingsManager.getTazikVoice()) {
            val text = makeNiceChange(purchaseStock.stock.ticker, change)
            speak(text)
        }
    }

    fun speakTazikSpikeSkip(purchaseStock: PurchaseStock, change: Double) {
        if (SettingsManager.getTazikVoice() && SettingsManager.getTelegramSendSpikes()) {
            val text = makeNiceChange(purchaseStock.stock.ticker, change)
            speak("спайк. $text")
        }
    }

    fun speakTrend(trendStock: TrendStock) {
        if (SettingsManager.getTrendVoice()) {
            val text = makeNiceChange(trendStock.stock.ticker, trendStock.changeFromStartToLow)
            val type = if (trendStock.changeFromStartToLow < 0) "Отскок вверх️" else "Отскок вниз"
            speak("$type $text") // ${trendStock.timeFromStartToLow} -> ${trendStock.timeFromStartToLow} мин -> ")
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