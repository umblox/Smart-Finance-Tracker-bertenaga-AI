package com.smartfinance.tracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.smartfinance.tracker.data.model.*
import com.smartfinance.tracker.data.local.dao.*

@Database(
    entities = [
        Transaction::class, 
        Category::class, 
        Debt::class, 
        Budget::class, 
        RecurringTransaction::class, 
        AiNotification::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun debtDao(): DebtDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringTxDao(): RecurringTxDao
    abstract fun aiNotificationDao(): AiNotificationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_finance_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
