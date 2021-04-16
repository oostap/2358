package com.project.ti2358.data.manager

import android.os.Build
import android.speech.tts.TextToSpeech
import com.project.ti2358.TheApplication
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import java.util.*

@KoinApiExtension
class StrategySpeaker() : KoinComponent, TextToSpeech.OnInitListener {
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
//                    Utils.showToastAlert("TTS не запущен 1")
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
            val name = rocketStock.stock.instrument.name
            val type = if (rocketStock.changePercent > 0) "ракета" else "комета"
            speak("%s %s %.2f%% %d мин".format(type, name, rocketStock.changePercent, rocketStock.time))
        }
    }

    fun speakTazik(purchaseStock: PurchaseStock, change: Double) {
        if (SettingsManager.getTazikVoice()) {
            val name = purchaseStock.stock.instrument.name
            speak("Заявка на покупку %s просадка %.2f%%".format(name, change))
        }
    }
}