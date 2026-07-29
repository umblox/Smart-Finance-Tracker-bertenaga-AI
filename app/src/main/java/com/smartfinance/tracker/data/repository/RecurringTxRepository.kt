package com.smartfinance.tracker.data.repository

import com.smartfinance.tracker.data.local.DatabaseProvider
import com.smartfinance.tracker.data.model.RecurringTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.HashMap

class RecurringTxRepository {
    private val dao = DatabaseProvider.db.recurringTxDao()
    private var listenJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _schedules = MutableStateFlow<List<RecurringTransaction>>(emptyList())
    val schedules: StateFlow<List<RecurringTransaction>> = _schedules

    fun startListening() {
        if (listenJob != null) return
        listenJob = scope.launch {
            dao.getAll().collect { list ->
                _schedules.value = list
            }
        }
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
    }

    suspend fun saveSchedule(docId: String?, data: HashMap<String, Any?>) {
        val id = docId ?: "rec_${System.currentTimeMillis()}"
        val createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        
        val tx = RecurringTransaction(
            id = id,
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
            createdAt = createdAt
        )
        dao.insert(tx)
    }

    suspend fun deleteSchedule(docId: String) {
        dao.delete(docId)
    }
}
