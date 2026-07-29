package com.smartfinance.tracker.ui.transaction

import androidx.lifecycle.ViewModel
import com.smartfinance.tracker.data.model.Transaction
import com.smartfinance.tracker.data.repository.DebtRepository
import com.smartfinance.tracker.data.repository.TransactionRepository
import com.smartfinance.tracker.data.local.DatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.ArrayList
import java.util.HashMap

class TransactionViewModel : ViewModel() {
    private val txRepository = TransactionRepository()
    private val debtRepository = DebtRepository()

    val transactions: StateFlow<List<Transaction>> = txRepository.transactions

    init {
        txRepository.startListening()
    }

    // 🔥 FIX: Mengambil data kategori menggunakan DAO secara Asinkron
    suspend fun getCategoriesForDropdown(): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        val list = ArrayList<Map<String, Any>>()
        val allCats = DatabaseProvider.db.categoryDao().getAllSync()
        for (cat in allCats) {
            val mutableData = HashMap<String, Any>()
            mutableData["id"] = cat.id
            mutableData["name"] = cat.name
            mutableData["type"] = cat.type
            list.add(mutableData)
        }
        list
    }

    suspend fun saveTransaction(txId: String, txMap: HashMap<String, Any>) {
        txRepository.saveTransaction(txId, txMap)
    }

    suspend fun deleteTransaction(txId: String) {
        txRepository.deleteTransaction(txId)
    }

    suspend fun saveDebt(debtId: String, debtMap: HashMap<String, Any>) {
        debtRepository.saveDebt(debtId, debtMap)
    }

    suspend fun updateDebtFields(debtId: String, contactName: String, amount: Double, type: String, timestamp: Long) {
        debtRepository.updateDebtFields(debtId, mapOf(
            "contactName" to contactName,
            "amount" to amount,
            "remainingAmount" to amount,
            "type" to type,
            "timestamp" to timestamp
        ))
    }

    suspend fun deleteDebt(debtId: String) {
        debtRepository.deleteDebt(debtId)
    }

    override fun onCleared() {
        super.onCleared()
        txRepository.stopListening()
    }
}
