package com.smartfinance.tracker.ui.debt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfinance.tracker.data.model.Debt
import com.smartfinance.tracker.data.repository.DebtRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
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
    
    private val _uiState = MutableStateFlow(DebtUiState())
    val uiState: StateFlow<DebtUiState> = _uiState

    private var currentCalendar = Calendar.getInstance()
    private var activeTab = "DEBT"
    private val sdfMonthLabel = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))

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
            currentMonthLabel = sdfMonthLabel.format(currentCalendar.time).uppercase(Locale.ROOT),
            totalActiveDebt = totalDebt,
            totalActiveReceivable = totalReceivable,
            displayedDebts = activeTabFiltered,
            currentTab = activeTab
        )
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopListening()
    }
}
