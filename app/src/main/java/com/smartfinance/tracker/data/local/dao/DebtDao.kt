package com.smartfinance.tracker.data.local.dao

import androidx.room.*
import com.smartfinance.tracker.data.local.entity.DebtEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(debt: DebtEntity): Long

    @Query("SELECT * FROM debts ORDER BY timestamp DESC")
    fun getAllDebts(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE isPaid = 0")
    suspend fun getUnpaidDebts(): List<DebtEntity>

    // Fitur Pembayaran Hutang: Mengurangi sisa hutang atau menandainya lunas
    @Query("UPDATE debts SET remainingAmount = :newRemaining, isPaid = :isPaid WHERE id = :debtId")
    suspend fun updateDebtPayment(debtId: Long, newRemaining: Double, isPaid: Boolean)
}

