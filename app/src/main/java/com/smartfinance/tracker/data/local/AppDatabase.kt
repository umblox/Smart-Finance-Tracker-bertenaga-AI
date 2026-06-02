package com.smartfinance.tracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.smartfinance.tracker.data.local.dao.CategoryDao
import com.smartfinance.tracker.data.local.dao.DebtDao
import com.smartfinance.tracker.data.local.dao.TransactionDao
import com.smartfinance.tracker.data.local.entity.CategoryEntity
import com.smartfinance.tracker.data.local.entity.DebtEntity
import com.smartfinance.tracker.data.local.entity.TransactionEntity

@Database(
    entities = [CategoryEntity::class, TransactionEntity::class, DebtEntity::class],
    version = 1,
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
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

