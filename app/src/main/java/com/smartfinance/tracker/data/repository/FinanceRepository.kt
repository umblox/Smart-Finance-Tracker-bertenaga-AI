package com.smartfinance.tracker.data.repository

import com.smartfinance.tracker.data.local.dao.CategoryDao
import com.smartfinance.tracker.data.local.dao.DebtDao
import com.smartfinance.tracker.data.local.dao.TransactionDao
import com.smartfinance.tracker.data.local.entity.CategoryEntity
import com.smartfinance.tracker.data.local.entity.DebtEntity
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

class FinanceRepository(
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val debtDao: DebtDao
) {
    // Kategori
    val allCategories: Flow<List<CategoryEntity>> = categoryDao.getAllCategories()
    suspend fun insertCategory(category: CategoryEntity) = categoryDao.insertCategory(category)
    suspend fun updateCategory(category: CategoryEntity) = categoryDao.updateCategory(category)
    suspend fun deleteCategory(category: CategoryEntity) = categoryDao.deleteCategory(category)

    // Transaksi
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    suspend fun insertTransaction(transaction: TransactionEntity) = transactionDao.insertTransaction(transaction)
    suspend fun getTransactionsByDateRange(start: Long, end: Long) = transactionDao.getTransactionsByDateRange(start, end)
    suspend fun getTransactionsByCategory(category: String) = transactionDao.getTransactionsByCategory(category)
    suspend fun getTotalIncome(start: Long, end: Long) = transactionDao.getTotalIncome(start, end)
    suspend fun getTotalExpense(start: Long, end: Long) = transactionDao.getTotalExpense(start, end)

    // Hutang Piutang
    val allDebts: Flow<List<DebtEntity>> = debtDao.getAllDebts()
    suspend fun insertDebt(debt: DebtEntity) = debtDao.insertDebt(debt)
    suspend fun updateDebtPayment(id: Long, remaining: Double, isPaid: Boolean) = debtDao.updateDebtPayment(id, remaining, isPaid)
}

