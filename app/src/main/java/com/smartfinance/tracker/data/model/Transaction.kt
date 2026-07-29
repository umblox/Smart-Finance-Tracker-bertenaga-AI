package com.smartfinance.tracker.data.model
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey val id: String = "",
    val amount: Double = 0.0,
    val type: String = "EXPENSE",
    val timestamp: Long = 0L,
    val categoryId: Long = 0L,
    val categoryName: String = "Umum",
    val note: String = "Transaksi AI",
    val debtId: String = ""
)
