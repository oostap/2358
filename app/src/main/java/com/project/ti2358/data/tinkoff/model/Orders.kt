package com.project.ti2358.data.tinkoff.model

data class Orders (
    val trackingId: String,
    val status: String,

    val payload: List<TinkoffOrder>
)