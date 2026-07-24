package com.smartfinance.tracker.data.model

data class RecurringTransaction(
    val id: String = "",
    val note: String = "",
    val amount: Double = 0.0,
    val type: String = "EXPENSE",
    val categoryId: Long = 15L,
    val categoryName: String = "Umum",
    val contactName: String = "",
    val interval: String = "MONTHLY",
    val nextExecutionTime: Long = 0L,
    val hasEndDate: Boolean = false,
    val endDate: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Long = 0L
)

