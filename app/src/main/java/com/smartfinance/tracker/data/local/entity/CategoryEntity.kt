package com.smartfinance.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L, // Diubah ke Long
    val name: String,
    val type: String, // "EXPENSE", "INCOME", atau "DEBT"
    val iconName: String = "ic_custom",
    val parentCategoryId: Long? = null, // Diubah ke Long
    val isLocked: Boolean = false
)
