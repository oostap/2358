package com.project.ti2358.data.manager

import com.project.ti2358.data.alor.service.StreamingAlorService
import com.project.ti2358.data.daager.service.ThirdPartyService
import kotlinx.coroutines.*
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@KoinApiExtension
class AlorAuthManager : KoinComponent {
    private val thirdPartyService: ThirdPartyService by inject()
    private val streamingAlorService: StreamingAlorService by inject()

    private var refreshJob: Job? = null

    companion object {
        var TOKEN: String = ""
    }

    suspend fun refreshToken() {
        try {
            TOKEN = thirdPartyService.alorRefreshToken("https://oauth.alor.ru/refresh")
            streamingAlorService.resubscribe()
        } catch (e: Exception) {
            e.printStackTrace()
            delay(1000)
        }
    }

    suspend fun startRefreshToken() {
        refreshJob?.cancel()
        refreshJob = GlobalScope.launch(Dispatchers.Default) {
            while (true) {
                refreshToken()
                delay(1000 * 300)
            }
        }
    }

    fun isAuthorized(): Boolean {
        return TOKEN.isNotEmpty()
    }
}
