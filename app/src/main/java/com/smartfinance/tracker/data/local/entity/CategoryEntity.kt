package com.smartfinance.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String, // INCOME atau EXPENSE
    val iconName: String,
    val parentCategoryId: Long? = null // NULL jika Kategori Induk, terisi ID jika Sub-Kategori
)
