package com.smartfinance.tracker.ui.debt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfinance.tracker.data.model.Debt
import com.smartfinance.tracker.data.repository.DebtRepository
import com.smartfinance.tracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.HashMap
import java.util.Locale

data class DebtUiState(
    val currentMonthLabel: String = "",
    val totalActiveDebt: Double = 0.0,
    val totalActiveReceivable: Double = 0.0,
    val displayedDebts: List<Debt> = emptyList(),
    val currentTab: String = "DEBT"
)

class DebtViewModel : ViewModel() {
    private val repository = DebtRepository()
    private val txRepository = TransactionRepository()
    
    private val _uiState = MutableStateFlow(DebtUiState())
    val uiState: StateFlow<DebtUiState> = _uiState

    private var currentCalendar = Calendar.getInstance()
    private var activeTab = "DEBT"
    
    // 🔥 FIX: Menggunakan Locale.getDefault() agar bulan beradaptasi dengan bahasa HP
    private val sdfMonthLabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    init {
        repository.startListening()
        viewModelScope.launch {
            repository.debts.collect { allDebts ->
                processDebts(allDebts)
            }
        }
    }

    fun changeMonth(amount: Int) {
        currentCalendar.add(Calendar.MONTH, amount)
        processDebts(repository.debts.value)
    }

    fun changeTab(tab: String) {
        activeTab = tab
        processDebts(repository.debts.value)
    }

    fun getCurrentTimeInMillis(): Long = currentCalendar.timeInMillis

    private fun processDebts(allDebts: List<Debt>) {
        var totalDebt = 0.0
        var totalReceivable = 0.0

        val targetMonth = currentCalendar.get(Calendar.MONTH)
        val targetYear = currentCalendar.get(Calendar.YEAR)

        val monthlyFilteredDebts = mutableListOf<Debt>()

        for (debt in allDebts) {
            if (!debt.isPaid) {
                if (debt.type == "DEBT") totalDebt += debt.remainingAmount
                else totalReceivable += debt.remainingAmount
            }

            val txCal = Calendar.getInstance().apply { timeInMillis = debt.timestamp }
            if (txCal.get(Calendar.MONTH) == targetMonth && txCal.get(Calendar.YEAR) == targetYear) {
                monthlyFilteredDebts.add(debt)
            }
        }

        val activeTabFiltered = monthlyFilteredDebts
            .filter { it.type == activeTab }
            .sortedByDescending { it.timestamp }

        _uiState.value = DebtUiState(
            // 🔥 FIX: Uppercase disesuaikan dengan Locale sistem
            currentMonthLabel = sdfMonthLabel.format(currentCalendar.time).uppercase(Locale.getDefault()),
            totalActiveDebt = totalDebt,
            totalActiveReceivable = totalReceivable,
            displayedDebts = activeTabFiltered,
            currentTab = activeTab
        )
    }

    suspend fun saveNewDebtAndTransaction(debtId: String, debtMap: HashMap<String, Any>, txId: String, txMap: HashMap<String, Any>) {
        repository.saveDebt(debtId, debtMap)
        txRepository.saveTransaction(txId, txMap) 
    }

    suspend fun processDebtInstallment(debtId: String, newRemaining: Double, isPaid: Boolean, txId: String, txMap: HashMap<String, Any>) {
        repository.updateDebtFields(debtId, mapOf("remainingAmount" to newRemaining, "isPaid" to isPaid))
        txRepository.saveTransaction(txId, txMap)
    }

    suspend fun deleteDebtPermanently(debtId: String) {
        repository.deleteDebt(debtId)
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopListening()
    }
}
