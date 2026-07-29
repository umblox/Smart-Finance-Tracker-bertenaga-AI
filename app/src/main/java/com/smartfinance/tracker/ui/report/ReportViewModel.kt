package com.smartfinance.tracker.ui.report

import android.graphics.Color
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
    
    // Data untuk Pie Chart Donat
    val topIncomeValues: List<Float> = emptyList(),
    val topIncomeColors: List<Int> = emptyList(),
    val topExpenseValues: List<Float> = emptyList(),
    val topExpenseColors: List<Int> = emptyList(),
    
    // Data Kartu Hutang/Piutang
    val totalHutang: Double = 0.0,    // Kategori ID 101L (Atau Tipe INCOME & DEBT)
    val totalPiutang: Double = 0.0,   // Kategori ID 104L (Atau Tipe EXPENSE & RECEIVABLE)
    val totalLainnya: Double = 0.0    // Pembayaran Utang (102L) & Penagihan Piutang (103L)
)

class ReportViewModel : ViewModel() {
    private val repository = TransactionRepository()

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState

    private var currentFilter = TimeFilter.MONTHLY
    private var baseTimeMillis = System.currentTimeMillis()

    // Palet warna cerah ala Money Lover untuk Pie Chart
    private val chartColors = listOf(
        Color.parseColor("#14B8A6"), // Teal
        Color.parseColor("#F59E0B"), // Amber
        Color.parseColor("#3B82F6"), // Blue
        Color.parseColor("#EC4899"), // Pink
        Color.parseColor("#8B5CF6")  // Purple
    )

    init {
        repository.startListening()
        viewModelScope.launch {
            repository.transactions.collect { allTx -> recalculateData(allTx) }
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

        var incCurr = 0.0; var expCurr = 0.0
        var debt = 0.0; var receivable = 0.0; var others = 0.0
        
        val incCategories = HashMap<String, Double>()
        val expCategories = HashMap<String, Double>()

        allTx.forEach { tx ->
            if (tx.timestamp in currentRange.first..currentRange.second) {
                // Hitung Pemasukan & Pengeluaran Utama
                if (tx.type == "INCOME" || tx.type == "DEBT") {
                    incCurr += tx.amount
                    incCategories[tx.categoryName] = (incCategories[tx.categoryName] ?: 0.0) + tx.amount
                }
                if (tx.type == "EXPENSE" || tx.type == "RECEIVABLE") {
                    expCurr += tx.amount
                    expCategories[tx.categoryName] = (expCategories[tx.categoryName] ?: 0.0) + tx.amount
                }

                // Klasifikasi Kartu Hutang/Piutang berdasarkan ID Kategori Khusus
                when (tx.categoryId) {
                    101L -> debt += tx.amount
                    104L -> receivable += tx.amount
                    102L, 103L -> others += tx.amount
                }
            }
        }

        val label = when (currentFilter) {
            TimeFilter.DAILY -> "Hari Ini"
            TimeFilter.WEEKLY -> "Minggu Ini"
            TimeFilter.MONTHLY -> "Bulan Ini"
        }

        _uiState.value = ReportUiState(
            filterLabel = label,
            incomeCurrent = incCurr,
            expenseCurrent = expCurr,
            netBalance = incCurr - expCurr,
            
            // Ambil 5 Kategori Teratas untuk Donat
            topIncomeValues = incCategories.values.sortedDescending().take(5).map { it.toFloat() },
            topIncomeColors = chartColors.take(incCategories.size.coerceAtMost(5)),
            topExpenseValues = expCategories.values.sortedDescending().take(5).map { it.toFloat() },
            topExpenseColors = chartColors.take(expCategories.size.coerceAtMost(5)),
            
            totalHutang = debt,
            totalPiutang = receivable,
            totalLainnya = others
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

        startCal.set(Calendar.HOUR_OF_DAY, 0); startCal.set(Calendar.MINUTE, 0); startCal.set(Calendar.SECOND, 0); startCal.set(Calendar.MILLISECOND, 0)
        endCal.set(Calendar.HOUR_OF_DAY, 23); endCal.set(Calendar.MINUTE, 59); endCal.set(Calendar.SECOND, 59); endCal.set(Calendar.MILLISECOND, 999)

        return Pair(startCal.timeInMillis, endCal.timeInMillis)
    }
}
