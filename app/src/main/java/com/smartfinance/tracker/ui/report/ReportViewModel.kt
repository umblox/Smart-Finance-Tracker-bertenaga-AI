package com.smartfinance.tracker.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfinance.tracker.data.model.Transaction
import com.smartfinance.tracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

enum class TimeFilter { DAILY, WEEKLY, MONTHLY }

data class ReportUiState(
    val filterLabel: String = "Bulan Ini",
    val incomeCurrent: Double = 0.0,
    val expenseCurrent: Double = 0.0,
    val netBalance: Double = 0.0,
    val incomePrevious: Double = 0.0,
    val expensePrevious: Double = 0.0,
    val topExpenses: List<Pair<String, Double>> = emptyList(),
    val topExpensesTotal: Double = 0.0,
    val hasData: Boolean = false,
    // 🔥 DATA UNTUK INSIGHT CERDAS
    val insightTitle: String = "💡 Statistik Pengeluaran",
    val insightAverageLabel: String = "Rata-rata pengeluaran:",
    val insightAverageValue: Double = 0.0,
    val insightProjectionLabel: String = "Proyeksi total akhir periode:",
    val insightProjectionValue: Double = 0.0
)

class ReportViewModel : ViewModel() {
    private val repository = TransactionRepository()

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState

    private var currentFilter = TimeFilter.MONTHLY
    private var baseTimeMillis = System.currentTimeMillis()

    init {
        repository.startListening()
        viewModelScope.launch {
            repository.transactions.collect { allTx ->
                recalculateData(allTx)
            }
        }
    }

    fun setTimeFilter(filter: TimeFilter, activeTimePrefs: Long = System.currentTimeMillis()) {
        currentFilter = filter
        baseTimeMillis = activeTimePrefs
        recalculateData(repository.transactions.value)
    }

    private fun recalculateData(allTx: List<Transaction>) {
        if (allTx.isEmpty()) {
            _uiState.value = ReportUiState()
            return
        }

        val currentRange = getTimeRange(currentFilter, 0, baseTimeMillis)
        val prevRange = getTimeRange(currentFilter, -1, baseTimeMillis)

        var incCurr = 0.0; var expCurr = 0.0
        var incPrev = 0.0; var expPrev = 0.0
        val currentPeriodExpenses = mutableListOf<Transaction>()

        allTx.forEach { tx ->
            val time = tx.timestamp
            
            if (time in currentRange.first..currentRange.second) {
                if (tx.type == "INCOME" || tx.type == "DEBT") incCurr += tx.amount
                if (tx.type == "EXPENSE" || tx.type == "RECEIVABLE") {
                    expCurr += tx.amount
                    currentPeriodExpenses.add(tx)
                }
            } else if (time in prevRange.first..prevRange.second) {
                if (tx.type == "INCOME" || tx.type == "DEBT") incPrev += tx.amount
                if (tx.type == "EXPENSE" || tx.type == "RECEIVABLE") expPrev += tx.amount
            }
        }

        val totalFilteredExpense = currentPeriodExpenses.sumOf { it.amount }
        val aggregated = currentPeriodExpenses.groupBy { it.categoryName }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }

        val label = when (currentFilter) {
            TimeFilter.DAILY -> "Hari Ini"
            TimeFilter.WEEKLY -> "Minggu Ini"
            TimeFilter.MONTHLY -> "Bulan Ini"
        }

        // =========================================
        // 🔥 LOGIKA KALKULASI PROYEKSI CERDAS
        // =========================================
        var avgLabel = ""
        var projLabel = ""
        var avgVal = 0.0
        var projVal = 0.0
        
        val calNow = Calendar.getInstance()
        val calBase = Calendar.getInstance().apply { timeInMillis = baseTimeMillis }
        val isCurrentYear = calNow.get(Calendar.YEAR) == calBase.get(Calendar.YEAR)
        
        when (currentFilter) {
            TimeFilter.DAILY -> {
                val isCurrentDay = isCurrentYear && calNow.get(Calendar.DAY_OF_YEAR) == calBase.get(Calendar.DAY_OF_YEAR)
                val elapsedHours = if (isCurrentDay) calNow.get(Calendar.HOUR_OF_DAY).coerceAtLeast(1) else 24
                avgVal = expCurr / elapsedHours
                projVal = avgVal * 24
                avgLabel = "Rata-rata pengeluaran per jam:"
                projLabel = "Proyeksi total hari ini:"
            }
            TimeFilter.WEEKLY -> {
                val isCurrentWeek = isCurrentYear && calNow.get(Calendar.WEEK_OF_YEAR) == calBase.get(Calendar.WEEK_OF_YEAR)
                var currentDayOfWeek = calNow.get(Calendar.DAY_OF_WEEK) - 1
                if (currentDayOfWeek == 0) currentDayOfWeek = 7 // Adjust agar Sen=1, Min=7
                val elapsedDays = if (isCurrentWeek) currentDayOfWeek.coerceAtLeast(1) else 7
                avgVal = expCurr / elapsedDays
                projVal = avgVal * 7
                avgLabel = "Rata-rata pengeluaran harian:"
                projLabel = "Proyeksi akhir minggu ini:"
            }
            TimeFilter.MONTHLY -> {
                val isCurrentMonth = isCurrentYear && calNow.get(Calendar.MONTH) == calBase.get(Calendar.MONTH)
                val totalDays = calBase.getActualMaximum(Calendar.DAY_OF_MONTH)
                val elapsedDays = if (isCurrentMonth) calNow.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1) else totalDays
                avgVal = expCurr / elapsedDays
                projVal = avgVal * totalDays
                avgLabel = "Rata-rata pengeluaran harian:"
                projLabel = "Proyeksi total akhir bulan:"
            }
        }

        _uiState.value = ReportUiState(
            filterLabel = label,
            incomeCurrent = incCurr,
            expenseCurrent = expCurr,
            netBalance = incCurr - expCurr,
            incomePrevious = incPrev,
            expensePrevious = expPrev,
            topExpenses = aggregated,
            topExpensesTotal = totalFilteredExpense,
            hasData = currentPeriodExpenses.isNotEmpty(),
            
            // Masukkan data insight ke UI State
            insightTitle = "💡 Statistik Pengeluaran $label",
            insightAverageLabel = avgLabel,
            insightAverageValue = avgVal,
            insightProjectionLabel = projLabel,
            insightProjectionValue = projVal
        )
    }

    private fun getTimeRange(filter: TimeFilter, offset: Int, timeMillis: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = timeMillis }
        
        when (filter) {
            TimeFilter.DAILY -> cal.add(Calendar.DAY_OF_YEAR, offset)
            TimeFilter.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, offset)
            TimeFilter.MONTHLY -> cal.add(Calendar.MONTH, offset)
        }

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

        startCal.set(Calendar.HOUR_OF_DAY, 0); startCal.set(Calendar.MINUTE, 0)
        startCal.set(Calendar.SECOND, 0); startCal.set(Calendar.MILLISECOND, 0)
        
        endCal.set(Calendar.HOUR_OF_DAY, 23); endCal.set(Calendar.MINUTE, 59)
        endCal.set(Calendar.SECOND, 59); endCal.set(Calendar.MILLISECOND, 999)

        return Pair(startCal.timeInMillis, endCal.timeInMillis)
    }
    
    fun getCurrentFilter(): TimeFilter = currentFilter
    fun getBaseTime(): Long = baseTimeMillis
    
    override fun onCleared() {
        super.onCleared()
        repository.stopListening()
    }
}
