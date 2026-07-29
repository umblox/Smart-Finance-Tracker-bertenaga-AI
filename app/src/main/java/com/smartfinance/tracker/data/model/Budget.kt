package com.smartfinance.tracker.data.model
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val id: String = "",
    val categoryId: Long = 0L,
    val categoryName: String = "",
    val limitAmount: Double = 0.0,
    val createdAt: Long = 0L
)
