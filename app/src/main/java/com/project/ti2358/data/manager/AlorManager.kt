package com.project.ti2358.data.manager

import com.project.ti2358.data.model.dto.*
import com.project.ti2358.data.model.dto.Currency
import com.project.ti2358.data.service.OrdersService
import com.project.ti2358.data.service.PortfolioService
import com.project.ti2358.data.service.StreamingAlorService
import com.project.ti2358.data.service.ThirdPartyService
import com.project.ti2358.service.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Collections.synchronizedList
import kotlin.math.abs

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
