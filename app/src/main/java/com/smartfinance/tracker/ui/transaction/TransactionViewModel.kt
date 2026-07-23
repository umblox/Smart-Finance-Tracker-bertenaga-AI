package com.smartfinance.tracker.ui.transaction

import androidx.lifecycle.ViewModel
import com.smartfinance.tracker.data.repository.DebtRepository
import com.smartfinance.tracker.data.repository.TransactionRepository
import com.smartfinance.tracker.utils.FirebaseManager
import kotlinx.coroutines.tasks.await
import java.util.ArrayList
import java.util.HashMap

class TransactionViewModel : ViewModel() {
    private val txRepository = TransactionRepository()
    private val debtRepository = DebtRepository()

    // Mengambil kategori khusus untuk ditampilkan di Spinner/Dropdown Dialog
    suspend fun getCategoriesForDropdown(): List<Map<String, Any>> {
        val firestore = FirebaseManager.getFirestore()
        val snapshot = firestore.collection("categories").get().await()
        val list = ArrayList<Map<String, Any>>()
        for (doc in snapshot.documents) {
            val data = doc.data ?: continue
            val mutableData = HashMap(data)
            mutableData["id"] = doc.getLong("id") ?: 0L
            list.add(mutableData)
        }
        return list
    }

    suspend fun saveTransaction(txId: String, txMap: HashMap<String, Any>) {
        txRepository.saveTransaction(txId, txMap)
    }

    suspend fun deleteTransaction(txId: String) {
        txRepository.deleteTransaction(txId)
    }

    // Delegasi tugas ke DebtRepository jika transaksi melibatkan Utang/Piutang
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
}
