package com.project.ti2358.data.daager.model

import com.project.ti2358.data.daager.model.IndexInfo

data class StockIndexComponents (
    val indices: Map<String, IndexInfo>,
    val data: Map<String, Map<String, Double>>,
) {
    fun getShortName(name: String): String {
        return indices[name]?.short ?: ""
    }
}