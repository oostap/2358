package com.project.ti2358.data.manager

import com.project.ti2358.data.service.StreamingAlorService
import com.project.ti2358.data.service.ThirdPartyService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@KoinApiExtension
class AlorManager : KoinComponent {
    private val thirdPartyService: ThirdPartyService by inject()
    private val streamingAlorService: StreamingAlorService by inject()
    companion object {
        var TOKEN: String = ""
    }

    fun refreshToken() {
        GlobalScope.launch(Dispatchers.Main) {
            try {
                TOKEN = thirdPartyService.alorRefreshToken("https://oauth.alor.ru/refresh")
                streamingAlorService.resubscribe()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun isAuthorized(): Boolean {
        return TOKEN.isNotEmpty()
    }
}
