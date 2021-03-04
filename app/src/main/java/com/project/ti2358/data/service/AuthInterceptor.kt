package com.project.ti2358.data.service

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor() : Interceptor {

    companion object {
        const val AUTHORIZATION_HEADER = "Authorization"
        const val BEARER_PREFIX = "Bearer "
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        if (chain.request().url.host.contains("tinkoff")) {
            return chain.proceed(
                chain.request().newBuilder().addHeader(
                    AUTHORIZATION_HEADER,
                    BEARER_PREFIX + SettingsManager.getActiveToken()
                ).build()
            )
        }
        return chain.proceed(chain.request().newBuilder().build())
    }
}