package com.smartfinance.tracker.ui.transaction

import androidx.lifecycle.ViewModel
import com.smartfinance.tracker.data.model.Transaction
import com.smartfinance.tracker.data.repository.DebtRepository
import com.smartfinance.tracker.data.repository.TransactionRepository
import com.smartfinance.tracker.utils.FirebaseManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import java.util.ArrayList
import java.util.Calendar
import java.util.HashMap

class TransactionViewModel : ViewModel() {
    private val txRepository = TransactionRepository()
    private val debtRepository = DebtRepository()

    val transactions: StateFlow<List<Transaction>> = txRepository.transactions

    init {
        // Default saat pertama dibuka: Kita amankan dengan rentang Bulan Ini
        val cal = Calendar.getInstance()
        val start = cal.apply { 
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        val end = cal.apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        
        loadDataForTimeRange(start, end)
    }

    // 🔥 FITUR BARU: MESIN WAKTU ABSOLUT (Harian, Mingguan, Bulanan, Tahunan)
    // Fragment / UI tinggal melempar millisecond titik awal dan titik akhir!
    fun loadDataForTimeRange(startTime: Long, endTime: Long) {
        txRepository.startListening(startTime, endTime)
    }

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
