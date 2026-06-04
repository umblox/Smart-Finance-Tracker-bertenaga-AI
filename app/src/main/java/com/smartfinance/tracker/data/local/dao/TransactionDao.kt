package com.smartfinance.tracker.data.local.dao

import androidx.room.*
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    // Kueri untuk Dashboard & Laporan berdasarkan rentang waktu (Harian, Bulanan, Tahunan)
    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getTransactionsByDateRange(startTime: Long, endTime: Long): List<TransactionEntity>

    // Kueri untuk laporan per kategori
    @Query("SELECT * FROM transactions WHERE categoryName = :categoryName")
    suspend fun getTransactionsByCategory(categoryName: String): List<TransactionEntity>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'INCOME' AND timestamp BETWEEN :startTime AND :endTime")
    suspend fun getTotalIncome(startTime: Long, endTime: Long): Double?

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'EXPENSE' AND timestamp BETWEEN :startTime AND :endTime")
    suspend fun getTotalExpense(startTime: Long, endTime: Long): Double?

    // PERBAIKAN MUTLAK: Sediakan fungsi hapus riwayat kas yang sah untuk Room SQLite
    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)
}
