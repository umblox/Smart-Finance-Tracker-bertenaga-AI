package com.smartfinance.tracker.data.repository

import com.google.firebase.firestore.ListenerRegistration
import com.smartfinance.tracker.data.model.Budget
import com.smartfinance.tracker.utils.FirebaseManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import java.util.HashMap

class BudgetRepository {
    private val firestore = FirebaseManager.getFirestore()
    private var listener: ListenerRegistration? = null

    private val _budgets = MutableStateFlow<List<Budget>>(emptyList())
    val budgets: StateFlow<List<Budget>> = _budgets

    fun startListening() {
        if (listener != null) return
        listener = firestore.collection("budgets")
            .orderBy("createdAt")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                
                val list = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    Budget(
                        id = doc.id,
                        categoryId = (data["categoryId"] as? Number)?.toLong() ?: 0L,
                        categoryName = data["categoryName"] as? String ?: "",
                        limitAmount = (data["limitAmount"] as? Number)?.toDouble() ?: 0.0,
                        createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L
                    )
                }
                _budgets.value = list
            }
    }

    fun stopListening() {
        listener?.remove()
        listener = null
    }

    suspend fun saveBudget(docId: String?, data: HashMap<String, Any>) {
        if (docId == null) {
            data["createdAt"] = System.currentTimeMillis()
            firestore.collection("budgets").add(data).await()
        } else {
            firestore.collection("budgets").document(docId).update(data).await()
        }
    }

    suspend fun deleteBudget(docId: String) {
        firestore.collection("budgets").document(docId).delete().await()
    }
}

