package com.project.ti2358.data.manager

import com.project.ti2358.data.alor.service.AlorPortfolioService
import com.project.ti2358.data.alor.service.StreamingAlorService
import com.project.ti2358.data.daager.service.ThirdPartyService
import com.project.ti2358.service.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@KoinApiExtension
class AlorAuthManager : KoinComponent {
    private val thirdPartyService: ThirdPartyService by inject()
    private val streamingAlorService: StreamingAlorService by inject()

    companion object {
        var TOKEN: String = ""
    }

    suspend fun refreshToken() {
//        GlobalScope.launch(Dispatchers.Default) {
            try {
                TOKEN = thirdPartyService.alorRefreshToken("https://oauth.alor.ru/refresh")
                streamingAlorService.resubscribe()
            } catch (e: Exception) {
                e.printStackTrace()
            }
//        }
    }

    fun isAuthorized(): Boolean {
        return TOKEN.isNotEmpty()
    }
}
