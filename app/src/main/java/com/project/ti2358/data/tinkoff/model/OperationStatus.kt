package com.project.ti2358.data.tinkoff.model

import com.google.gson.annotations.SerializedName

enum class OperationStatus {
    @SerializedName("Done") DONE,
    @SerializedName("Decline") DECLINE,
    @SerializedName("Progress") PROGRESS,
}