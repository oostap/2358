package com.project.ti2358.data.tinkoff.model

import com.project.ti2358.data.tinkoff.model.Order

data class Orders (
    val trackingId: String,
    val status: String,

    val payload: List<Order>
)