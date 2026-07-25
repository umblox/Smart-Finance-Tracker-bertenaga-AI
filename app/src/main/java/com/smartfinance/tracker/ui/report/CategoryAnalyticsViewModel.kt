package com.smartfinance.tracker.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfinance.tracker.data.model.Transaction
import com.smartfinance.tracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class CategoryAnalyticsUiState(
    val categoryName: String = "",
    val timeLabel: String = "",
    val totalSpent: Double = 0.0,
    val transactions: List<Transaction> = emptyList(),
    val isEmpty: Boolean = true
)

class CategoryAnalyticsViewModel : ViewModel() {
    private val repository = TransactionRepository()
    private val _uiState = MutableStateFlow(CategoryAnalyticsUiState())
    val uiState: StateFlow<CategoryAnalyticsUiState> = _uiState

    init { repository.startListening() }

    fun loadCategoryData(categoryName: String, timeFilterString: String, baseTimeMillis: Long) {
        viewModelScope.launch {
            repository.transactions.collect { allTx ->
                val timeFilter = try { TimeFilter.valueOf(timeFilterString) } catch (e: Exception) { TimeFilter.MONTHLY }
                val timeRange = getTimeRange(timeFilter, baseTimeMillis)
                
                // Filter 1: Rentang Waktu
                // Filter 2: Nama Kategori Sama
                val filteredTx = allTx.filter { tx ->
                    tx.timestamp in timeRange.first..timeRange.second && tx.categoryName == categoryName
                }.sortedByDescending { it.timestamp }

                val total = filteredTx.sumOf { it.amount }
                
                val label = when (timeFilter) {
                    TimeFilter.DAILY -> "Hari Ini"
                    TimeFilter.WEEKLY -> "Minggu Ini"
                    TimeFilter.MONTHLY -> "Bulan Ini"
                }

                _uiState.value = CategoryAnalyticsUiState(
                    categoryName = categoryName,
                    timeLabel = label,
                    totalSpent = total,
                    transactions = filteredTx,
                    isEmpty = filteredTx.isEmpty()
                )
            }
        }
    }

    private fun getTimeRange(filter: TimeFilter, timeMillis: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = timeMillis }
        val startCal = cal.clone() as Calendar
        val endCal = cal.clone() as Calendar

        when (filter) {
            TimeFilter.DAILY -> { }
            TimeFilter.WEEKLY -> {
                startCal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                endCal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                endCal.add(Calendar.WEEK_OF_YEAR, 1)
            }
            TimeFilter.MONTHLY -> {
                startCal.set(Calendar.DAY_OF_MONTH, 1)
                endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH))
            }
        }

        startCal.set(Calendar.HOUR_OF_DAY, 0); startCal.set(Calendar.MINUTE, 0); startCal.set(Calendar.SECOND, 0); startCal.set(Calendar.MILLISECOND, 0)
        endCal.set(Calendar.HOUR_OF_DAY, 23); endCal.set(Calendar.MINUTE, 59); endCal.set(Calendar.SECOND, 59); endCal.set(Calendar.MILLISECOND, 999)
        return Pair(startCal.timeInMillis, endCal.timeInMillis)
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopListening()
    }
}
