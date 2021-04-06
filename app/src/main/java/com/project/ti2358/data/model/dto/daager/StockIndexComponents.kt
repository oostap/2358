package com.project.ti2358.data.model.dto.daager

data class StockIndexComponents (
    val indices: Map<String, IndexInfo>,
    val data: Map<String, Map<String, Double>>,
) {
    fun getShortName(name: String): String {
        return indices[name]?.short ?: ""
    }
}