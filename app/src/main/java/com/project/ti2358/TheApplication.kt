package com.project.ti2358

import android.app.Application
import com.project.ti2358.data.manager.WorkflowManager
import com.project.ti2358.data.service.*
import org.koin.core.component.KoinApiExtension

@KoinApiExtension
class TheApplication : Application() {

    companion object {
        lateinit var application: TheApplication
    }

    override fun onCreate() {
        super.onCreate()

        application = this
        SettingsManager.setSettingsContext(applicationContext)
        WorkflowManager.startKoin()
    }
}