package com.project.ti2358.data.model.dto.daager

data class StockIndex (
    val indices: Map<String, IndexInfo>,
    val data: Map<String, List<String>>,
) {
    fun getShortName(name: String): String {
        return indices[name]?.short ?: ""
    }
}