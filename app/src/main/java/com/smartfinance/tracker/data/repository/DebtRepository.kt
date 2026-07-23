package com.smartfinance.tracker.data.repository

import com.google.firebase.firestore.ListenerRegistration
import com.smartfinance.tracker.data.model.Debt
import com.smartfinance.tracker.utils.FirebaseManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

class DebtRepository {
    private val firestore = FirebaseManager.getFirestore()
    private var listener: ListenerRegistration? = null

    private val _debts = MutableStateFlow<List<Debt>>(emptyList())
    val debts: StateFlow<List<Debt>> = _debts

    fun startListening() {
        if (listener != null) return
        listener = firestore.collection("debts")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener

                val list = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    Debt(
                        id = doc.id,
                        contactName = data["contactName"] as? String ?: "TEMAN",
                        contactPhoneNumber = data["contactPhoneNumber"] as? String ?: "",
                        amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                        remainingAmount = (data["remainingAmount"] as? Number)?.toDouble() ?: 0.0,
                        type = data["type"] as? String ?: "DEBT",
                        note = data["note"] as? String ?: "",
                        timestamp = (data["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                        isPaid = data["isPaid"] as? Boolean ?: false
                    )
                }
                _debts.value = list
            }
    }

    fun stopListening() {
        listener?.remove()
        listener = null
    }

    suspend fun saveDebt(debtId: String, debtMap: HashMap<String, Any>) {
        firestore.collection("debts").document(debtId).set(debtMap).await()
    }

    suspend fun updateDebtFields(debtId: String, updates: Map<String, Any>) {
        firestore.collection("debts").document(debtId).update(updates).await()
    }

    suspend fun deleteDebt(debtId: String) {
        firestore.collection("debts").document(debtId).delete().await()
    }
}
