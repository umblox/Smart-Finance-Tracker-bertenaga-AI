package com.smartfinance.tracker.data.model

data class AiNotification(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val timestamp: Long = 0L,
    val type: String = "INFO", // Tipe: BUDGET, RECURRING, WEEKLY_REPORT, INFO
    var isRead: Boolean = false
)

