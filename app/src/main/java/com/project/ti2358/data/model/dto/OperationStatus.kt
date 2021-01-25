package com.project.ti2358.data.model.dto

import com.google.gson.annotations.SerializedName

enum class OperationStatus {
    @SerializedName("Done") DONE,
    @SerializedName("Decline") DECLINE,
    @SerializedName("Progress") PROGRESS,
}