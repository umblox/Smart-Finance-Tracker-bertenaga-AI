package com.smartfinance.tracker.data.model

data class Debt(
    val id: String = "",
    val contactName: String = "TEMAN",
    val contactPhoneNumber: String = "",
    val amount: Double = 0.0,
    val remainingAmount: Double = 0.0,
    val type: String = "DEBT",
    val note: String = "",
    val timestamp: Long = 0L,
    val isPaid: Boolean = false
)

