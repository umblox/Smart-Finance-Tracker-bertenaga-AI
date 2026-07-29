package com.smartfinance.tracker.data.model
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey val id: String = "",
    val contactName: String = "TEMAN",
    val contactPhoneNumber: String = "",
    val amount: Double = 0.0,
    val remainingAmount: Double = 0.0,
    val type: String = "DEBT",
    val note: String = "",
    val timestamp: Long = 0L,
    val isPaid: Boolean = false
)
