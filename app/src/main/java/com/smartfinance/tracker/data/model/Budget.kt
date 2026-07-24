package com.smartfinance.tracker.data.model

data class Budget(
    val id: String = "",
    val categoryId: Long = 0L,
    val categoryName: String = "",
    val limitAmount: Double = 0.0,
    val createdAt: Long = 0L
)

