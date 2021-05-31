package com.project.ti2358.data.service

import com.project.ti2358.data.manager.SettingsManager
import okhttp3.Interceptor
import okhttp3.Response
import org.koin.core.component.KoinApiExtension

class AuthInterceptor() : Interceptor {

    companion object {
        const val AUTHORIZATION_HEADER = "Authorization"
        const val BEARER_PREFIX = "Bearer "
    }

    @KoinApiExtension
    override fun intercept(chain: Interceptor.Chain): Response {
        if ("d" + "a" + "a" + "g" + "e" + "r" in chain.request().url.host || "o" + "o" + "s" + "t" in chain.request().url.host) {
            return chain.proceed(
                chain.request().newBuilder().addHeader(
                    AUTHORIZATION_HEADER,
                    BEARER_PREFIX + "bnVybGFuJm9zdGFwIGNvbGxhYg"
                ).build()
            )
        }

        if ("api-invest.tinkoff.ru" in chain.request().url.host) {
            return chain.proceed(
                chain.request().newBuilder().addHeader(
                    AUTHORIZATION_HEADER,
                    BEARER_PREFIX + SettingsManager.getTokenTinkoff()
                ).build()
            )
        }

        return chain.proceed(chain.request().newBuilder().build())
    }
}