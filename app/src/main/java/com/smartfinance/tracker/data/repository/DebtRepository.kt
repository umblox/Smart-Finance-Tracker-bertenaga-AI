package com.smartfinance.tracker.data.repository

import com.smartfinance.tracker.data.local.DatabaseProvider
import com.smartfinance.tracker.data.model.Debt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.HashMap

class DebtRepository {
    private val dao = DatabaseProvider.db.debtDao()
    private var listenJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _debts = MutableStateFlow<List<Debt>>(emptyList())
    val debts: StateFlow<List<Debt>> = _debts

    fun startListening() {
        if (listenJob != null) return
        listenJob = scope.launch {
            dao.getAll().collect { list ->
                _debts.value = list
            }
        }
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
    }

    suspend fun saveDebt(debtId: String, data: HashMap<String, Any>) {
        val debt = Debt(
            id = debtId,
            contactName = data["contactName"] as? String ?: "TEMAN",
            contactPhoneNumber = data["contactPhoneNumber"] as? String ?: "",
            amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
            remainingAmount = (data["remainingAmount"] as? Number)?.toDouble() ?: 0.0,
            type = data["type"] as? String ?: "DEBT",
            note = data["note"] as? String ?: "",
            timestamp = (data["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            isPaid = data["isPaid"] as? Boolean ?: false
        )
        dao.insert(debt)
    }

    suspend fun updateDebtFields(debtId: String, updates: Map<String, Any>) {
        val existingDebt = dao.getById(debtId) ?: return
        val updatedDebt = existingDebt.copy(
            remainingAmount = (updates["remainingAmount"] as? Number)?.toDouble() ?: existingDebt.remainingAmount,
            isPaid = updates["isPaid"] as? Boolean ?: existingDebt.isPaid,
            contactName = updates["contactName"] as? String ?: existingDebt.contactName,
            amount = (updates["amount"] as? Number)?.toDouble() ?: existingDebt.amount,
            type = updates["type"] as? String ?: existingDebt.type,
            timestamp = (updates["timestamp"] as? Number)?.toLong() ?: existingDebt.timestamp
        )
        dao.update(updatedDebt)
    }

    suspend fun deleteDebt(debtId: String) {
        dao.delete(debtId)
    }
}
