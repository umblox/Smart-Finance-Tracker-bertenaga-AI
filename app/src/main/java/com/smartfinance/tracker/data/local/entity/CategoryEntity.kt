package com.smartfinance.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String, // "EXPENSE", "INCOME", atau "DEBT"
    val iconName: String = "ic_custom",
    val parentCategoryId: Int? = null,
    val isLocked: Boolean = false // FLAG UNTUK MENGUNCI KATEGORI BAWAAN SISTEM
)
