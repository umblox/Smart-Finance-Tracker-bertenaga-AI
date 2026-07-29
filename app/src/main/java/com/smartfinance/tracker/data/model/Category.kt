package com.smartfinance.tracker.data.model
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val docId: String = "",
    val id: Long = 0L,
    val name: String = "",
    val type: String = "EXPENSE",
    val iconName: String = "ic_custom",
    val parentCategoryId: Long? = null,
    val isLocked: Boolean = false
)
