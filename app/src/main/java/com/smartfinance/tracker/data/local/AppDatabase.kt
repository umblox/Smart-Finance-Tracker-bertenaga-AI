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
import java.util.concurrent.Executors

@Database(
    entities = [CategoryEntity::class, TransactionEntity::class, DebtEntity::class],
    version = 5, // Naikkan versi ke 5 karena ada perubahan struktur skema tabel
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
                .fallbackToDestructiveMigration() // Otomatis reset tabel dengan aman jika skema berubah
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        Executors.newSingleThreadExecutor().execute {
                            val database = getDatabase(context)
                            database.categoryDao().insertDefaultCategories(getHierarchicalDefaultCategories())
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        private fun getHierarchicalDefaultCategories(): List<CategoryEntity> {
            return listOf(
                // Kategori Induk Pemasukan
                CategoryEntity(id = 1, name = "Gaji & Pendapatan", type = "INCOME", iconName = "ic_salary"),
                CategoryEntity(id = 2, name = "Investasi", type = "INCOME", iconName = "ic_investment"),
                
                // Kategori Induk Pengeluaran (Sesuai Gambar Referensi)
                CategoryEntity(id = 3, name = "Belanja", type = "EXPENSE", iconName = "ic_shopping"),
                CategoryEntity(id = 4, name = "Makanan & Minuman", type = "EXPENSE", iconName = "ic_food"),
                CategoryEntity(id = 5, name = "Transportasi", type = "EXPENSE", iconName = "ic_transport"),
                CategoryEntity(id = 6, name = "Hadiah & Donasi", type = "EXPENSE", iconName = "ic_gift"),
                CategoryEntity(id = 7, name = "Lain-lain / Umum", type = "EXPENSE", iconName = "ic_others"),
                
                // SUB-KATEGORI DARI BELANJA (parentCategoryId = 3)
                CategoryEntity(id = 8, name = "Arneta.id", type = "EXPENSE", iconName = "ic_arneta", parentCategoryId = 3),
                CategoryEntity(id = 9, name = "Berkebun", type = "EXPENSE", iconName = "ic_garden", parentCategoryId = 3),
                CategoryEntity(id = 10, name = "Dandan", type = "EXPENSE", iconName = "ic_beauty", parentCategoryId = 3),
                CategoryEntity(id = 11, name = "Keperluan Pribadi", type = "EXPENSE", iconName = "ic_personal", parentCategoryId = 3),
                CategoryEntity(id = 12, name = "Peralatan Rumah Tangga", type = "EXPENSE", iconName = "ic_home", parentCategoryId = 3),
                CategoryEntity(id = 13, name = "Sandangan", type = "EXPENSE", iconName = "ic_clothes", parentCategoryId = 3),

                // SUB-KATEGORI DARI MAKANAN & MINUMAN (parentCategoryId = 4)
                CategoryEntity(id = 14, name = "Jajan & Rokok", type = "EXPENSE", iconName = "ic_cigarette", parentCategoryId = 4)
            )
        }
    }
}
