package com.smartfinance.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactName: String,
    val contactPhoneNumber: String,
    val amount: Double,
    val remainingAmount: Double,
    val type: String, // "DEBT" atau "RECEIVABLE"
    val note: String,
    val timestamp: Long,
    val isPaid: Boolean = false
)
