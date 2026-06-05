package com.smartfinance.tracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smartfinance.tracker.data.local.dao.CategoryDao
import com.smartfinance.tracker.data.local.dao.TransactionDao
import com.smartfinance.tracker.data.local.dao.DebtDao
import com.smartfinance.tracker.data.local.entity.CategoryEntity
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import com.smartfinance.tracker.data.local.entity.DebtEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [CategoryEntity::class, TransactionEntity::class, DebtEntity::class], 
    version = 6, // Naikkan versi ke 6 karena skema tabel diperbarui dengan flag isLocked
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun debtDao(): DebtDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_finance_db"
                )
                .fallbackToDestructiveMigration() // Reset otomatis jika ada konflik versi skema lama
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Solusi Bug: Gunakan trigger internal thread executor agar data mutlak tersimpan saat inisialisasi
                        CoroutineScope(Dispatchers.IO).launch {
                            val dao = getDatabase(context).categoryDao()
                            
                            // 1. KATEGORI UMUM (Bisa diubah/dihapus oleh user, isLocked = false)
                            dao.insertCategory(CategoryEntity(id = 1, name = "Gaji & Pendapatan", type = "INCOME", iconName = "ic_salary", isLocked = false))
                            dao.insertCategory(CategoryEntity(id = 2, name = "Investasi", type = "INCOME", iconName = "ic_investment", isLocked = false))
                            dao.insertCategory(CategoryEntity(id = 3, name = "Belanja", type = "EXPENSE", iconName = "ic_shopping", isLocked = false))
                            dao.insertCategory(CategoryEntity(id = 4, name = "Makanan & Minuman", type = "EXPENSE", iconName = "ic_food", isLocked = false))
                            dao.insertCategory(CategoryEntity(id = 5, name = "Transportasi", type = "EXPENSE", iconName = "ic_transport", isLocked = false))
                            dao.insertCategory(CategoryEntity(id = 6, name = "Hadiah & Donasi", type = "EXPENSE", iconName = "ic_gift", isLocked = false))
                            dao.insertCategory(CategoryEntity(id = 7, name = "Lain-lain / Umum", type = "EXPENSE", iconName = "ic_others", isLocked = false))
                            
                            // SUB-KATEGORI (isLocked = false)
                            dao.insertCategory(CategoryEntity(id = 8, name = "Arneta.id", type = "EXPENSE", iconName = "ic_arneta", parentCategoryId = 3, isLocked = false))
                            dao.insertCategory(CategoryEntity(id = 9, name = "Berkebun", type = "EXPENSE", iconName = "ic_garden", parentCategoryId = 3, isLocked = false))
                            dao.insertCategory(CategoryEntity(id = 10, name = "Dandan", type = "EXPENSE", iconName = "ic_beauty", parentCategoryId = 3, isLocked = false))
                            dao.insertCategory(CategoryEntity(id = 14, name = "Jajan & Rokok", type = "EXPENSE", iconName = "ic_cigarette", parentCategoryId = 4, isLocked = false))

                            // 2. KATEGORI HUTANG PIUTANG BAWAAN SISTEM (Mutlak dikunci, isLocked = true)
                            dao.insertCategory(CategoryEntity(id = 101, name = "Hutang", type = "DEBT", iconName = "ic_debt_in", isLocked = true))
                            dao.insertCategory(CategoryEntity(id = 102, name = "Pembayaran kembali", type = "DEBT", iconName = "ic_debt_out", isLocked = true))
                            dao.insertCategory(CategoryEntity(id = 103, name = "Penagihan Utang", type = "DEBT", iconName = "ic_receivable_in", isLocked = true))
                            dao.insertCategory(CategoryEntity(id = 104, name = "Piutang", type = "DEBT", iconName = "ic_receivable_out", isLocked = true))
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
