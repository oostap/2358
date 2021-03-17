package com.project.ti2358

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.multidex.MultiDex
import androidx.multidex.MultiDexApplication
import com.project.ti2358.data.manager.SettingsManager
import com.project.ti2358.data.manager.WorkflowManager
import org.koin.core.component.KoinApiExtension

class TheApplication : MultiDexApplication() {

    companion object {
        lateinit var application: TheApplication
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    @KoinApiExtension
    override fun onCreate() {
        super.onCreate()

        application = this
        SettingsManager.setSettingsContext(applicationContext)

        if (SettingsManager.getDarkTheme()) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        WorkflowManager.startKoin()
    }
}