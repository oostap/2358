package com.project.ti2358.data.model.dto.reports

data class Report (
    val date_format: String,
    val date: Long,

    val tod: String,
    val estimate_rev_per: Double?,
    val estimate_eps: Double?,

    val actual_rev_per: Double?,
    val actual_eps: Double?,
)
