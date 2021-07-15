package com.project.ti2358.data.common

import retrofit2.Retrofit

open class BaseOrder() {
    open fun getLotsExecuted(): Int { return 0 }
    open fun getLotsRequested(): Int { return 0 }
    open fun getBrokerColor(): Int { return 0 }
}