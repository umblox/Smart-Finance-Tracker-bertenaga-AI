package com.smartfinance.tracker.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfinance.tracker.data.model.Transaction
import com.smartfinance.tracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class ReportUiState(
    val incomeThisMonth: Double = 0.0,
    val expenseThisMonth: Double = 0.0,
    val netBalance: Double = 0.0,
    val incomeLastMonth: Double = 0.0,
    val expenseLastMonth: Double = 0.0,
    val topExpenses: List<Pair<String, Double>> = emptyList(),
    val topExpensesTotal: Double = 0.0,
    val dailyAvg: Double = 0.0,
    val projectedTotal: Double = 0.0,
    val hasData: Boolean = false
)

class ReportViewModel : ViewModel() {
    private val repository = TransactionRepository()

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState

    init {
        repository.startListening()
    }

    fun calculateReport(activeTimePrefs: Long) {
        viewModelScope.launch {
            repository.transactions.collect { allTx ->
                val calToday = Calendar.getInstance().apply { timeInMillis = activeTimePrefs }
                val calLastMonth = Calendar.getInstance().apply { timeInMillis = activeTimePrefs; add(Calendar.MONTH, -1) }

                var incThis = 0.0
                var expThis = 0.0
                var incLast = 0.0
                var expLast = 0.0

                val currentMonthExpenses = mutableListOf<Transaction>()

                allTx.forEach { tx ->
                    val txCal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
                    val isThisMonth = txCal.get(Calendar.MONTH) == calToday.get(Calendar.MONTH) && txCal.get(Calendar.YEAR) == calToday.get(Calendar.YEAR)
                    val isLastMonth = txCal.get(Calendar.MONTH) == calLastMonth.get(Calendar.MONTH) && txCal.get(Calendar.YEAR) == calLastMonth.get(Calendar.YEAR)

                    if (isThisMonth) {
                        if (tx.type == "INCOME" || tx.type == "DEBT") incThis += tx.amount
                        if (tx.type == "EXPENSE" || tx.type == "RECEIVABLE") {
                            expThis += tx.amount
                            currentMonthExpenses.add(tx)
                        }
                    }
                    if (isLastMonth) {
                        if (tx.type == "INCOME" || tx.type == "DEBT") incLast += tx.amount
                        if (tx.type == "EXPENSE" || tx.type == "RECEIVABLE") expLast += tx.amount
                    }
                }

                // Kalkulasi Top Boros
                val totalFilteredExpense = currentMonthExpenses.sumOf { it.amount }
                val aggregated = currentMonthExpenses.groupBy { it.categoryName }
                    .mapValues { entry -> entry.value.sumOf { it.amount } }
                    .toList()
                    .sortedByDescending { it.second }

                // Kalkulasi Insight Cerdas
                val dayOfMonth = calToday.get(Calendar.DAY_OF_MONTH)
                val daysInMonth = calToday.getActualMaximum(Calendar.DAY_OF_MONTH)
                var dailyAvg = 0.0
                var projectedTotal = 0.0

                if (expThis > 0 && dayOfMonth > 0) {
                    dailyAvg = expThis / dayOfMonth
                    projectedTotal = dailyAvg * daysInMonth
                }

                _uiState.value = ReportUiState(
                    incomeThisMonth = incThis,
                    expenseThisMonth = expThis,
                    netBalance = incThis - expThis,
                    incomeLastMonth = incLast,
                    expenseLastMonth = expLast,
                    topExpenses = aggregated,
                    topExpensesTotal = totalFilteredExpense,
                    dailyAvg = dailyAvg,
                    projectedTotal = projectedTotal,
                    hasData = currentMonthExpenses.isNotEmpty()
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopListening()
    }
}
