package com.smartfinance.tracker.ui.transaction

import androidx.lifecycle.ViewModel
import com.smartfinance.tracker.utils.FirebaseManager
import kotlinx.coroutines.tasks.await
import java.util.HashMap

class TransactionActionViewModel : ViewModel() {
    private val firestore = FirebaseManager.getFirestore()

    suspend fun getCategoriesCloud(): List<Map<String, Any>> {
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
        firestore.collection("transactions").document(txId).set(txMap).await()
    }

    suspend fun deleteTransaction(txId: String) {
        firestore.collection("transactions").document(txId).delete().await()
    }

    suspend fun saveDebt(debtId: String, debtMap: HashMap<String, Any>) {
        firestore.collection("debts").document(debtId).set(debtMap).await()
    }

    suspend fun updateDebt(debtId: String, contactName: String, amount: Double, type: String, timestamp: Long) {
        firestore.collection("debts").document(debtId).update(
            "contactName", contactName,
            "amount", amount,
            "remainingAmount", amount,
            "type", type,
            "timestamp", timestamp
        ).await()
    }

    suspend fun updateDebtPayment(debtId: String, newRemaining: Double, isPaid: Boolean) {
        firestore.collection("debts").document(debtId).update(
            "remainingAmount", newRemaining,
            "isPaid", isPaid
        ).await()
    }

    suspend fun deleteDebt(debtId: String) {
        firestore.collection("debts").document(debtId).delete().await()
    }
}
