package com.project.ti2358

import android.app.Application
import com.project.ti2358.data.manager.WorkflowManager
import com.project.ti2358.data.service.*
import com.project.ti2358.service.Utils
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class TheApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        SettingsManager.setSettingsContext(applicationContext)
        Utils.setApplicationContext(applicationContext)
        WorkflowManager.startKoin()
    }
}