package com.smartfinance.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactName: String, // Nama dari Kontak HP
    val contactPhoneNumber: String,
    val amount: Double, // Total nominal hutang/piutang awal
    val remainingAmount: Double, // Sisa yang belum dibayar
    val type: String, // "DEBT" (Kita berhutang) atau "RECEIVABLE" (Orang berhutang ke kita)
    val note: String,
    val timestamp: Long,
    val isPaid: Boolean = false
)

