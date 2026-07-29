package com.smartfinance.tracker.data.local

import android.content.Context
import com.smartfinance.tracker.data.model.Category
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DatabaseProvider {
    lateinit var db: AppDatabase
    
    fun init(context: Context) {
        db = AppDatabase.getDatabase(context)

        CoroutineScope(Dispatchers.IO).launch {
            val dao = db.categoryDao()
            
            if (dao.getAllSync().isEmpty()) {
                val defaultCategories = listOf(
                    // 1. Kategori Umum Pendapatan & Pengeluaran
                    Category(docId = "cat_1", id = 1L, name = "Gaji & Pendapatan", type = "INCOME", iconName = "ic_custom", parentCategoryId = null, isLocked = true),
                    Category(docId = "cat_15", id = 15L, name = "Lain-lain / Umum", type = "EXPENSE", iconName = "ic_custom", parentCategoryId = null, isLocked = true),
                    
                    // 2. Kategori Wajib Hutang Piutang (Sesuai ID di sistem FinancialAssistant)
                    Category(docId = "cat_101", id = 101L, name = "Hutang", type = "DEBT", iconName = "ic_custom", parentCategoryId = null, isLocked = true),
                    Category(docId = "cat_102", id = 102L, name = "Pembayaran kembali", type = "DEBT", iconName = "ic_custom", parentCategoryId = null, isLocked = true),
                    Category(docId = "cat_103", id = 103L, name = "Penagihan Utang", type = "DEBT", iconName = "ic_custom", parentCategoryId = null, isLocked = true),
                    Category(docId = "cat_104", id = 104L, name = "Piutang", type = "DEBT", iconName = "ic_custom", parentCategoryId = null, isLocked = true)
                )
                
                defaultCategories.forEach { dao.insert(it) }
            }
        }
    }
}
