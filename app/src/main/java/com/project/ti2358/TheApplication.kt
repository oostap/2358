package com.project.ti2358

import android.app.Application
import android.util.Log
import com.project.ti2358.data.manager.WorkflowManager
import com.project.ti2358.data.service.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.component.KoinApiExtension
import org.koin.core.context.startKoin
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@KoinApiExtension
class TheApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        SettingsManager.setSettingsContext(applicationContext)
        WorkflowManager.startKoin()
    }
}