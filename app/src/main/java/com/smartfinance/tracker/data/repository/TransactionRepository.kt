package com.smartfinance.tracker.data.repository

import com.smartfinance.tracker.data.local.DatabaseProvider
import com.smartfinance.tracker.data.model.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.HashMap

class TransactionRepository {
    private val dao = DatabaseProvider.db.transactionDao()
    private var listenJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions

    fun startListening() {
        if (listenJob != null) return
        listenJob = scope.launch {
            dao.getAll().collect { list ->
                _transactions.value = list
            }
        }
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
    }

    suspend fun saveTransaction(txId: String, txMap: HashMap<String, Any>) {
        val tx = Transaction(
            id = txId,
            amount = (txMap["amount"] as? Number)?.toDouble() ?: 0.0,
            type = (txMap["type"] as? String ?: "EXPENSE").trim().uppercase(Locale.ROOT),
            timestamp = (txMap["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            categoryId = (txMap["categoryId"] as? Number)?.toLong() ?: 0L,
            categoryName = txMap["categoryName"] as? String ?: "Umum",
            note = txMap["note"] as? String ?: "Transaksi AI",
            debtId = txMap["debtId"] as? String ?: ""
        )
        dao.insert(tx)
    }

    suspend fun deleteTransaction(txId: String) {
        dao.delete(txId)
    }
}
