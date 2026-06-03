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
    version = 4,
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
                // Menyuntikkan fallback migrasi destruktif aman jika versi naik
                .fallbackToDestructiveMigration()
                // REGISTER LOGIKA SUNTIK OTOMATIS SAAT DATABASE DI-CREAT PERTAMA KALI
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Jalankan di Background Thread agar aplikasi tidak freeze saat instalasi awal
                        Executors.newSingleThreadExecutor().execute {
                            val database = getDatabase(context)
                            database.categoryDao().insertDefaultCategories(get15DefaultCategories())
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        // KUMPULAN 15 KATEGORI DEFAULT PRESET PREMIUM (ANTI-KOSONG)
        private fun get15DefaultCategories(): List<CategoryEntity> {
            return listOf(
                CategoryEntity(id = 1, name = "Gaji & Pendapatan", type = "INCOME"),
                CategoryEntity(id = 2, name = "Makanan & Minuman", type = "EXPENSE"),
                CategoryEntity(id = 3, name = "Belanja & Kebutuhan", type = "EXPENSE"),
                CategoryEntity(id = 4, name = "Transportasi & Bensin", type = "EXPENSE"),
                CategoryEntity(id = 5, name = "Tagihan & Listrik", type = "EXPENSE"),
                CategoryEntity(id = 6, name = "Hiburan & Hiburan", type = "EXPENSE"),
                CategoryEntity(id = 7, name = "Kesehatan & Medis", type = "EXPENSE"),
                CategoryEntity(id = 8, name = "Edukasi & Buku", type = "EXPENSE"),
                CategoryEntity(id = 9, name = "Investasi & Saham", type = "INCOME"),
                CategoryEntity(id = 10, name = "Hadiah & Donasi", type = "EXPENSE"),
                CategoryEntity(id = 11, name = "Cicilan & Pinjaman", type = "EXPENSE"),
                CategoryEntity(id = 12, name = "Hutang (Saya Meminjam)", type = "INCOME"),
                CategoryEntity(id = 13, name = "Piutang (Memberi Pinjaman)", type = "EXPENSE"),
                CategoryEntity(id = 14, name = "Bonus & Sampingan", type = "INCOME"),
                CategoryEntity(id = 15, name = "Lain-lain / Umum", type = "EXPENSE")
            )
        }
    }
}
