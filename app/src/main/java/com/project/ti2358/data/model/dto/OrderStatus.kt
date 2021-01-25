package com.project.ti2358.data.model.dto

import com.google.gson.annotations.SerializedName

enum class OrderStatus {
    @SerializedName("New") NEW,
    @SerializedName("PartiallyFill") PARTIALLY_FILL,
    @SerializedName("Fill") FILL,
    @SerializedName("Cancelled") CANCELLED,
    @SerializedName("Replaced") REPLACED,
    @SerializedName("PendingCancel") PENDING_CANCEL,
    @SerializedName("Rejected") REJECTED,
    @SerializedName("PendingReplace") PENDING_REPLACE,
    @SerializedName("PendingNew") PENDING_NEW
}