package com.smartfinance.tracker.utils

import com.google.gson.Gson
import com.smartfinance.tracker.data.local.DatabaseProvider
import com.smartfinance.tracker.data.model.BackupData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object BackupEngine {
    suspend fun exportDbToJson(): String = withContext(Dispatchers.IO) {
        val db = DatabaseProvider.db
        val data = BackupData(
            transactions = db.transactionDao().getAll().first(),
            categories = db.categoryDao().getAll().first(),
            debts = db.debtDao().getAll().first(),
            budgets = db.budgetDao().getAll().first(),
            recurringTransactions = db.recurringTxDao().getAll().first(),
            aiNotifications = db.aiNotificationDao().getAll().first()
        )
        Gson().toJson(data)
    }

    // Fungsi asli untuk sinkronisasi Google Drive
    suspend fun importJsonToDb(jsonString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val data = Gson().fromJson(jsonString, BackupData::class.java)
            val db = DatabaseProvider.db
            
            db.clearAllTables()
            
            // 🔥 Fix Warning: Hapus safe-call (?.) karena compiler membaca list ini sudah pasti non-nullable
            data.categories.forEach { db.categoryDao().insert(it) }
            data.transactions.forEach { db.transactionDao().insert(it) }
            data.debts.forEach { db.debtDao().insert(it) }
            data.budgets.forEach { db.budgetDao().insert(it) }
            data.recurringTransactions.forEach { db.recurringTxDao().insert(it) }
            data.aiNotifications.forEach { db.aiNotificationDao().insert(it) }
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Fungsi khusus untuk Import Lokal agar pesan Error ASLI bisa dilempar ke UI
    suspend fun importJsonToDbLocal(jsonString: String) = withContext(Dispatchers.IO) {
        val data = Gson().fromJson(jsonString, BackupData::class.java)
        val db = DatabaseProvider.db
        
        db.clearAllTables()
        
        data.categories.forEach { db.categoryDao().insert(it) }
        data.transactions.forEach { db.transactionDao().insert(it) }
        data.debts.forEach { db.debtDao().insert(it) }
        data.budgets.forEach { db.budgetDao().insert(it) }
        data.recurringTransactions.forEach { db.recurringTxDao().insert(it) }
        data.aiNotifications.forEach { db.aiNotificationDao().insert(it) }
    }
}
