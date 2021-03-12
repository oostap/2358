package com.project.ti2358

import android.app.Application
import com.project.ti2358.data.manager.SettingsManager
import com.project.ti2358.data.manager.WorkflowManager
import org.koin.core.component.KoinApiExtension

class TheApplication : Application() {

    companion object {
        lateinit var application: TheApplication
    }

    @KoinApiExtension
    override fun onCreate() {
        super.onCreate()

        application = this
        SettingsManager.setSettingsContext(applicationContext)
        WorkflowManager.startKoin()
    }
}