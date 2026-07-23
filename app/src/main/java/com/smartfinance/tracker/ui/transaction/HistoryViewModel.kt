package com.smartfinance.tracker.ui.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfinance.tracker.data.model.Transaction
import com.smartfinance.tracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.collections.LinkedHashMap

// Data state yang akan dipantau oleh Fragment
data class HistoryUiState(
    val currentMonthLabel: String = "",
    val groupedTransactions: LinkedHashMap<String, List<Transaction>> = LinkedHashMap(),
    val isEmpty: Boolean = true
)

class HistoryViewModel : ViewModel() {
    private val repository = TransactionRepository()
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState

    private var currentCalendar = Calendar.getInstance()
    private val sdfLabel = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))
    private val sdfDay = SimpleDateFormat("EEEE, dd MMM yyyy", Locale("id", "ID"))

    init {
        repository.startListening()
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

        // Filter data sesuai bulan yang sedang aktif
        val monthlyList = allTx.filter { tx ->
            val txCal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
            txCal.get(Calendar.MONTH) == targetMonth && txCal.get(Calendar.YEAR) == targetYear
        }.sortedByDescending { it.timestamp }

        // Kelompokkan berdasarkan Hari/Tanggal
        val groupMap = LinkedHashMap<String, MutableList<Transaction>>()
        
        monthlyList.forEach { tx ->
            val dayHeaderString = sdfDay.format(Date(tx.timestamp))
            if (!groupMap.containsKey(dayHeaderString)) {
                groupMap[dayHeaderString] = mutableListOf()
            }
            groupMap[dayHeaderString]?.add(tx)
        }

        _uiState.value = HistoryUiState(
            currentMonthLabel = sdfLabel.format(currentCalendar.time).uppercase(Locale.ROOT),
            groupedTransactions = groupMap as LinkedHashMap<String, List<Transaction>>,
            isEmpty = monthlyList.isEmpty()
        )
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopListening()
    }
}
