package com.smartfinance.tracker.data.model

data class Category(
    val docId: String = "",
    val id: Long = 0L,
    val name: String = "",
    val type: String = "EXPENSE",
    val iconName: String = "ic_custom",
    val parentCategoryId: Long? = null,
    val isLocked: Boolean = false
)

