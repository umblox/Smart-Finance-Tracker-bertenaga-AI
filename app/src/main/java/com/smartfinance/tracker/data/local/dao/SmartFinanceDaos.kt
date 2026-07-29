package com.smartfinance.tracker.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.smartfinance.tracker.data.model.*

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAll(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllSync(): List<Transaction>

    @Query("SELECT * FROM transactions WHERE categoryId = :categoryId")
    suspend fun getByCategoryId(categoryId: Long): List<Transaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories")
    fun getAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories")
    fun getAllSync(): List<Category>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category)

    @Query("DELETE FROM categories WHERE docId = :docId")
    suspend fun delete(docId: String)
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY timestamp DESC")
    fun getAll(): Flow<List<Debt>>

    @Query("SELECT * FROM debts ORDER BY timestamp DESC")
    fun getAllSync(): List<Debt>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(debt: Debt)
    
    @Update
    suspend fun update(debt: Debt)

    @Query("SELECT * FROM debts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Debt?

    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY createdAt ASC")
    fun getAll(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId LIMIT 1")
    suspend fun getByCategoryId(categoryId: Long): Budget?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: Budget)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface RecurringTxDao {
    @Query("SELECT * FROM recurring_transactions ORDER BY createdAt ASC")
    fun getAll(): Flow<List<RecurringTransaction>>

    @Query("SELECT * FROM recurring_transactions WHERE isActive = 1 AND nextExecutionTime <= :time")
    suspend fun getDueTransactions(time: Long): List<RecurringTransaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recurringTx: RecurringTransaction)
    
    @Update
    suspend fun update(recurringTx: RecurringTransaction)

    @Query("DELETE FROM recurring_transactions WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface AiNotificationDao {
    @Query("SELECT * FROM ai_notifications ORDER BY timestamp DESC")
    fun getAll(): Flow<List<AiNotification>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: AiNotification)

    @Query("UPDATE ai_notifications SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllAsRead()

    @Query("DELETE FROM ai_notifications WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM ai_notifications WHERE timestamp < :cutoffTime")
    suspend fun deleteOldNotifications(cutoffTime: Long)
}
