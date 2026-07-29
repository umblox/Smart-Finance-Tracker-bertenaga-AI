package com.smartfinance.tracker.data.model
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_notifications")
data class AiNotification(
    @PrimaryKey val id: String = "",
    val title: String = "",
    val message: String = "",
    val timestamp: Long = 0L,
    val type: String = "INFO",
    var isRead: Boolean = false
)
