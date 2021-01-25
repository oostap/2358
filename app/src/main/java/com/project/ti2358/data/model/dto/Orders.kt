package com.project.ti2358.data.model.dto

data class Orders (
    val trackingId: String,
    val status: String,

    val payload: List<Order>
)