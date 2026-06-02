package com.smartfinance.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val type: String, // "INCOME" atau "EXPENSE"
    val categoryId: Long,
    val categoryName: String, // Denormalisasi agar AI lebih mudah membaca langsung
    val note: String,
    val timestamp: Long // Waktu transaksi dalam milidetik
)

