package com.smartfinance.tracker.data.repository

import com.google.firebase.firestore.ListenerRegistration
import com.smartfinance.tracker.data.model.Transaction
import com.smartfinance.tracker.utils.FirebaseManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class TransactionRepository {
    private val firestore = FirebaseManager.getFirestore()
    private var listener: ListenerRegistration? = null

    // StateFlow untuk memancarkan data secara reaktif
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions

    fun startListening() {
        if (listener != null) return // Mencegah double-listen
        listener = firestore.collection("transactions")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener

                val list = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    Transaction(
                        id = doc.id,
                        amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                        type = (data["type"] as? String ?: "EXPENSE").trim().uppercase(Locale.ROOT),
                        timestamp = (data["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                        categoryId = (data["categoryId"] as? Number)?.toLong() ?: 0L,
                        categoryName = data["categoryName"] as? String ?: "Umum",
                        note = data["note"] as? String ?: "Transaksi AI",
                        debtId = data["debtId"] as? String ?: ""
                    )
                }
                _transactions.value = list
            }
    }

    fun stopListening() {
        listener?.remove()
        listener = null
    }
}
