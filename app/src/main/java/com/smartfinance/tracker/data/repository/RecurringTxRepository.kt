package com.smartfinance.tracker.data.repository

import com.google.firebase.firestore.ListenerRegistration
import com.smartfinance.tracker.data.model.RecurringTransaction
import com.smartfinance.tracker.utils.FirebaseManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import java.util.HashMap

class RecurringTxRepository {
    private val firestore = FirebaseManager.getFirestore()
    private var listener: ListenerRegistration? = null

    private val _schedules = MutableStateFlow<List<RecurringTransaction>>(emptyList())
    val schedules: StateFlow<List<RecurringTransaction>> = _schedules

    fun startListening() {
        if (listener != null) return
        listener = firestore.collection("recurring_transactions")
            .orderBy("createdAt")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                val list = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    RecurringTransaction(
                        id = doc.id,
                        note = data["note"] as? String ?: "",
                        amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                        type = data["type"] as? String ?: "EXPENSE",
                        categoryId = (data["categoryId"] as? Number)?.toLong() ?: 15L,
                        categoryName = data["categoryName"] as? String ?: "Umum",
                        contactName = data["contactName"] as? String ?: "",
                        interval = data["interval"] as? String ?: "MONTHLY",
                        nextExecutionTime = (data["nextExecutionTime"] as? Number)?.toLong() ?: 0L,
                        hasEndDate = data["hasEndDate"] as? Boolean ?: false,
                        endDate = (data["endDate"] as? Number)?.toLong(),
                        isActive = data["isActive"] as? Boolean ?: true,
                        createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L
                    )
                }
                _schedules.value = list
            }
    }

    fun stopListening() {
        listener?.remove()
        listener = null
    }

    suspend fun saveSchedule(docId: String?, data: HashMap<String, Any?>) {
        if (docId == null) {
            data["createdAt"] = System.currentTimeMillis()
            firestore.collection("recurring_transactions").add(data).await()
        } else {
            firestore.collection("recurring_transactions").document(docId).update(data).await()
        }
    }

    suspend fun deleteSchedule(docId: String) {
        firestore.collection("recurring_transactions").document(docId).delete().await()
    }
}

