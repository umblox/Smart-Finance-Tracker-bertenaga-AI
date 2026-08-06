package com.smartfinance.tracker.ui.report

import android.app.Application
import android.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartfinance.tracker.R
import com.smartfinance.tracker.data.model.Transaction
import com.smartfinance.tracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

// Enum tetap dipertahankan agar tidak bentrok dengan file lain
enum class TimeFilter { DAILY, WEEKLY, MONTHLY }

data class TimeNavItemReport(val label: String, val timeMillis: Long, val isSelected: Boolean)

data class ReportUiState(
    val incomeCurrent: Double = 0.0,
    val expenseCurrent: Double = 0.0,
    val netBalance: Double = 0.0,
    
    val topIncomeValues: List<Float> = emptyList(),
    val topIncomeColors: List<Int> = emptyList(),
    val topExpenseValues: List<Float> = emptyList(),
    val topExpenseColors: List<Int> = emptyList(),
    
    val totalHutang: Double = 0.0,    
    val totalPiutang: Double = 0.0,   
    val totalLainnya: Double = 0.0,

    val timeNavItems: List<TimeNavItemReport> = emptyList(),
    val selectedTimeMillis: Long = 0L
)

// 🔥 FIX: Upgrade menjadi AndroidViewModel untuk mengakses dwibahasa dengan aman
class ReportViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TransactionRepository()

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState

    private var baseTimeMillis = System.currentTimeMillis()

    private val chartColors = listOf(
        Color.parseColor("#14B8A6"), 
        Color.parseColor("#F59E0B"), 
        Color.parseColor("#3B82F6"), 
        Color.parseColor("#EC4899"), 
        Color.parseColor("#8B5CF6")  
    )

    init {
        repository.startListening()
        viewModelScope.launch {
            repository.transactions.collect { allTx -> recalculateData(allTx) }
        }
    }

    fun setTimeMillis(activeTimePrefs: Long) {
        baseTimeMillis = activeTimePrefs
        recalculateData(repository.transactions.value)
    }

    private fun recalculateData(allTx: List<Transaction>) {
        val cal = Calendar.getInstance().apply { timeInMillis = baseTimeMillis }
        val targetMonth = cal.get(Calendar.MONTH)
        val targetYear = cal.get(Calendar.YEAR)

        var incCurr = 0.0; var expCurr = 0.0
        var debt = 0.0; var receivable = 0.0; var others = 0.0
        
        val incCategories = HashMap<String, Double>()
        val expCategories = HashMap<String, Double>()

        val currentMonthTx = allTx.filter { tx ->
            val txCal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
            txCal.get(Calendar.MONTH) == targetMonth && txCal.get(Calendar.YEAR) == targetYear
        }

        currentMonthTx.forEach { tx ->
            if (tx.type == "INCOME" || tx.type == "DEBT") {
                incCurr += tx.amount
                incCategories[tx.categoryName] = (incCategories[tx.categoryName] ?: 0.0) + tx.amount
            }
            if (tx.type == "EXPENSE" || tx.type == "RECEIVABLE") {
                expCurr += tx.amount
                expCategories[tx.categoryName] = (expCategories[tx.categoryName] ?: 0.0) + tx.amount
            }

            when (tx.categoryId) {
                101L -> debt += tx.amount
                104L -> receivable += tx.amount
                102L, 103L -> others += tx.amount
            }
        }

        _uiState.value = ReportUiState(
            incomeCurrent = incCurr,
            expenseCurrent = expCurr,
            netBalance = incCurr - expCurr,
            
            topIncomeValues = incCategories.values.sortedDescending().take(5).map { it.toFloat() },
            topIncomeColors = chartColors.take(incCategories.size.coerceAtMost(5)),
            topExpenseValues = expCategories.values.sortedDescending().take(5).map { it.toFloat() },
            topExpenseColors = chartColors.take(expCategories.size.coerceAtMost(5)),
            
            totalHutang = debt,
            totalPiutang = receivable,
            totalLainnya = others,

            timeNavItems = generateTimeNav(baseTimeMillis),
            selectedTimeMillis = baseTimeMillis
        )
    }

    private fun generateTimeNav(selectedTimeMillis: Long): List<TimeNavItemReport> {
        val list = mutableListOf<TimeNavItemReport>()
        val realNow = Calendar.getInstance()
        val realMonth = realNow.get(Calendar.MONTH)
        val realYear = realNow.get(Calendar.YEAR)

        val selCal = Calendar.getInstance().apply { timeInMillis = selectedTimeMillis }
        val selMonth = selCal.get(Calendar.MONTH)
        val selYear = selCal.get(Calendar.YEAR)

        val diffYears = realYear - selYear
        val diffMonths = (diffYears * 12) + (realMonth - selMonth)
        val totalTabs = maxOf(24, diffMonths + 6) 

        val iterCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, -(totalTabs - 1))
        }
        
        for (i in 0 until totalTabs) {
            val m = iterCal.get(Calendar.MONTH)
            val y = iterCal.get(Calendar.YEAR)
            
            // 🔥 FIX: Tarik string "BULAN INI" / "BULAN LALU" dari Resource XML dwibahasa
            val label = when {
                m == realMonth && y == realYear -> getApplication<Application>().getString(R.string.report_this_month)
                m == (realMonth - 1 + 12) % 12 && (if(realMonth==0) y==realYear-1 else y==realYear) -> getApplication<Application>().getString(R.string.report_last_month)
                else -> "${String.format(Locale.getDefault(), "%02d", m + 1)}/$y"
            }
            
            val isSelected = (m == selMonth && y == selYear)
            list.add(TimeNavItemReport(label, iterCal.timeInMillis, isSelected))
            
            iterCal.add(Calendar.MONTH, 1)
        }
        return list
    }

    fun getBaseTime(): Long = baseTimeMillis

    override fun onCleared() {
        super.onCleared()
        repository.stopListening()
    }
}
