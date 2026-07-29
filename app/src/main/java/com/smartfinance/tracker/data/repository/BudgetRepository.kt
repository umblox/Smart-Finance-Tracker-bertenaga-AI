package com.smartfinance.tracker.data.repository

import com.smartfinance.tracker.data.local.DatabaseProvider
import com.smartfinance.tracker.data.model.Budget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.HashMap

class BudgetRepository {
    private val dao = DatabaseProvider.db.budgetDao()
    private var listenJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _budgets = MutableStateFlow<List<Budget>>(emptyList())
    val budgets: StateFlow<List<Budget>> = _budgets

    fun startListening() {
        if (listenJob != null) return
        listenJob = scope.launch {
            dao.getAll().collect { list ->
                _budgets.value = list
            }
        }
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
    }

    suspend fun saveBudget(docId: String?, data: HashMap<String, Any>) {
        val id = docId ?: "bdg_${System.currentTimeMillis()}"
        val createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        
        val budget = Budget(
            id = id,
            categoryId = (data["categoryId"] as? Number)?.toLong() ?: 0L,
            categoryName = data["categoryName"] as? String ?: "",
            limitAmount = (data["limitAmount"] as? Number)?.toDouble() ?: 0.0,
            createdAt = createdAt
        )
        dao.insert(budget)
    }

    suspend fun deleteBudget(docId: String) {
        dao.delete(docId)
    }
}
