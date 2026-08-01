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
                    // ==========================================
                    // 🔒 1. KATEGORI SYSTEM (LOCKED - Jangan Diubah)
                    // ==========================================
                    Category(docId = "cat_101", id = 101L, name = "Hutang", type = "DEBT", iconName = "ic_custom", parentCategoryId = null, isLocked = true),
                    Category(docId = "cat_102", id = 102L, name = "Pembayaran kembali", type = "DEBT", iconName = "ic_custom", parentCategoryId = null, isLocked = true),
                    Category(docId = "cat_103", id = 103L, name = "Penagihan Utang", type = "DEBT", iconName = "ic_custom", parentCategoryId = null, isLocked = true),
                    Category(docId = "cat_104", id = 104L, name = "Piutang", type = "DEBT", iconName = "ic_custom", parentCategoryId = null, isLocked = true),

                    // ==========================================
                    // 📥 2. KATEGORI PEMASUKAN (INCOME)
                    // ==========================================
                    Category(docId = "cat_201", id = 201L, name = "Gaji", type = "INCOME", iconName = "ic_custom", parentCategoryId = null, isLocked = false),
                    Category(docId = "cat_202", id = 202L, name = "Bisnis", type = "INCOME", iconName = "ic_custom", parentCategoryId = null, isLocked = false),
                    Category(docId = "cat_203", id = 203L, name = "Investasi", type = "INCOME", iconName = "ic_custom", parentCategoryId = null, isLocked = false),
                    Category(docId = "cat_204", id = 204L, name = "Pemberian", type = "INCOME", iconName = "ic_custom", parentCategoryId = null, isLocked = false),
                    Category(docId = "cat_205", id = 205L, name = "Bonus & THR", type = "INCOME", iconName = "ic_custom", parentCategoryId = null, isLocked = false),
                    Category(docId = "cat_206", id = 206L, name = "Lain-lain", type = "INCOME", iconName = "ic_custom", parentCategoryId = null, isLocked = false),

                    // ==========================================
                    // 💸 3. KATEGORI PENGELUARAN (EXPENSE)
                    // ==========================================
                    // 3.1 Makanan & Minuman
                    Category(docId = "cat_301", id = 301L, name = "Makanan & Minuman", type = "EXPENSE", iconName = "ic_custom", parentCategoryId = null, isLocked = false),
                    Category(docId = "cat_3011", id = 3011L, name = "Makan di Luar", type = "EXPENSE", iconName = "ic_custom", parentCategoryId = 301L, isLocked = false),
                    Category(docId = "cat_3012", id = 3012L, name = "Belanja Dapur", type = "EXPENSE", iconName = "ic_custom", parentCategoryId = 301L, isLocked = false),
                    Category(docId = "cat_3013", id = 3013L, name = "Kopi & Jajanan", type = "EXPENSE", iconName = "ic_custom", parentCategoryId = 301L, isLocked = false),

                    // 3.2 Transportasi
                    Category(docId = "cat_302", id = 302L, name = "Transportasi", type = "EXPENSE", iconName = "ic_custom", parentCategoryId = null, isLocked = false),
                    Category(docId = "cat_3021", id = 3021L, name = "Bensin", type = "EXPENSE", iconName = "ic_custom", parentCategoryId = 302L, isLocked = false),
                    Category(docId = "cat_3022", id = 3022L, name = "Transportasi Umum", type = "EXPENSE", iconName = "ic_custom", parentCategoryId = 302L, isLocked = false),
                    Category(docId = "cat_3023", id = 3023L, name = "Parkir & Tol", type = "EXPENSE", iconName = "ic_custom", parentCategoryId = 302L, isLocked = false),
                    Category(docId = "cat_3024", id = 3024L, name = "Servis Kendaraan", type = "EXPENSE", iconName = "ic_custom", parentCategoryId = 302L, isLocked = false),

                    // 3.3 Kebutuhan Rumah
                    Category(docId = "cat_303", id = 303L, name = "Kebutuhan Rumah", type = "EXPENSE", iconName = "ic_custom", parentCategoryId = null, isLocked = false),
                    Category(docId = "cat_3031", id = 3031L, name = "Listrik & Air", type = "EXPENSE", iconName = "ic_custom", parentCategoryId = 303L, isLocked = false),
                    Category(docId = "cat_3032", id = 3032L, name = "Internet & Pulsa", type = "EXPENSE", iconName = "ic_custom", parentCategoryId = 303L, isLocked = false),

                    // 3.4 Belanja & Pribadi
                    Category(docId = "cat_304", id = 304L, name = "Belanja", type = "EXPENSE", iconName = "ic_custom", parentCategoryId = null, isLocked = false),
                    Category(docId = "cat_3041", id = 3041L, name = "Pakaian & Sepatu", type = "EXPENSE", iconName = "ic_custom", parentCategoryId = 304L, isLocked = false),
                    Category(docId = "cat_3042", id = 3042L, name = "Perawatan Diri", type = "EXPENSE", iconName = "ic_custom", parentCategoryId = 304L, isLocked = false),

                    // 3.5 Hiburan
                    Category(docId = "cat_305", id = 305L, name = "Hiburan", type = "EXPENSE", iconName = "ic_custom", parentCategoryId = null, isLocked = false),
                    Category(docId = "cat_3051", id = 3051L, name = "Film & Bioskop", type = "EXPENSE", iconName = "ic_custom", parentCategoryId = 305L, isLocked = false),
                    Category(docId = "cat_3052", id = 3052L, name = "Langganan Digital", type = "EXPENSE", iconName = "ic_custom", parentCategoryId = 305L, isLocked = false),

                    // 3.6 Sosial & Keluarga
                    Category(docId = "cat_306", id = 306L, name = "Sosial & Keluarga", type = "EXPENSE", iconName = "ic_custom", parentCategoryId = null, isLocked = false),
                    Category(docId = "cat_3061", id = 3061L, name = "Sedekah & Donasi", type = "EXPENSE", iconName = "ic_custom", parentCategoryId = 306L, isLocked = false),
                    Category(docId = "cat_3062", id = 3062L, name = "Hadiah & Kondangan", type = "EXPENSE", iconName = "ic_custom", parentCategoryId = 306L, isLocked = false),
                    
                    // 3.7 Keuangan & Admin
                    Category(docId = "cat_307", id = 307L, name = "Keuangan", type = "EXPENSE", iconName = "ic_custom", parentCategoryId = null, isLocked = false),
                    Category(docId = "cat_3071", id = 3071L, name = "Biaya Admin Bank", type = "EXPENSE", iconName = "ic_custom", parentCategoryId = 307L, isLocked = false),
                    
                    // Default cadangan (Untuk Jaga-jaga)
                    Category(docId = "cat_15", id = 15L, name = "Lain-lain / Umum", type = "EXPENSE", iconName = "ic_custom", parentCategoryId = null, isLocked = true)
                )
                
                defaultCategories.forEach { dao.insert(it) }
            }
        }
    }
}
