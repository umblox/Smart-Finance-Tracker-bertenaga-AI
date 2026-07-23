package com.smartfinance.tracker.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfinance.tracker.data.model.Transaction
import com.smartfinance.tracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class DetailCategoryUiState(
    val currentMonthLabel: String = "",
    val totalExpense: Double = 0.0,
    val groupedTransactions: Map<String, List<Transaction>> = emptyMap(),
    val isEmpty: Boolean = true,
    val activeTimeMillis: Long = 0L
)

class DetailCategoryViewModel : ViewModel() {
    private val repository = TransactionRepository()

    private val _uiState = MutableStateFlow(DetailCategoryUiState())
    val uiState: StateFlow<DetailCategoryUiState> = _uiState

    private var currentCalendar = Calendar.getInstance()
    private val sdfMonth = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))

    init {
        repository.startListening()
    }

    fun initializeTime(timeInMillis: Long) {
        currentCalendar.timeInMillis = timeInMillis
        // Amati perubahan langsung dari repository pusat
        viewModelScope.launch {
            repository.transactions.collect { allTx ->
                processTransactions(allTx)
            }
        }
    }

    fun changeMonth(amount: Int) {
        currentCalendar.add(Calendar.MONTH, amount)
        processTransactions(repository.transactions.value)
    }

    private fun processTransactions(allTx: List<Transaction>) {
        val targetMonth = currentCalendar.get(Calendar.MONTH)
        val targetYear = currentCalendar.get(Calendar.YEAR)

        val monthlyExpenses = allTx.filter { tx ->
            val cal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
            cal.get(Calendar.MONTH) == targetMonth && 
            cal.get(Calendar.YEAR) == targetYear && 
            (tx.type == "EXPENSE" || tx.type == "RECEIVABLE")
        }

        val totalExpense = monthlyExpenses.sumOf { it.amount }
        val grouped = monthlyExpenses.groupBy { it.categoryName }

        _uiState.value = DetailCategoryUiState(
            currentMonthLabel = sdfMonth.format(currentCalendar.time).uppercase(Locale.ROOT),
            totalExpense = totalExpense,
            groupedTransactions = grouped,
            isEmpty = monthlyExpenses.isEmpty(),
            activeTimeMillis = currentCalendar.timeInMillis
        )
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopListening()
    }
}
