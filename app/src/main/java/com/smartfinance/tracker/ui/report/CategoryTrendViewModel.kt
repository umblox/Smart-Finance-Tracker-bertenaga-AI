package com.smartfinance.tracker.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfinance.tracker.data.model.Transaction
import com.smartfinance.tracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class TrendItem(
    val label: String,
    val amount: Double,
    val percentage: Int = 0 
)

data class TimeNavItem(
    val label: String,
    val timeMillis: Long,
    val isSelected: Boolean
)

data class CategoryTrendUiState(
    val targetName: String = "Rincian Biaya",
    val targetMode: String = "ALL_EXPENSE",
    val isExpenseMode: Boolean = true,
    val totalAmount: Double = 0.0,
    val dailyAverage: Double = 0.0,
    val avg3Month: Double = 0.0,
    val diffFromAvg: Double = 0.0,
    val isAvgVisible: Boolean = false,
    val timeNavItems: List<TimeNavItem> = emptyList(),
    val selectedTimeMillis: Long = 0L,
    val breakdownItems: List<TrendItem> = emptyList(),
    val donutValues: List<Float> = emptyList(),
    val trendItems: List<TrendItem> = emptyList(),
    val trendBarValues: List<Float> = emptyList()
)

class CategoryTrendViewModel : ViewModel() {
    private val repository = TransactionRepository()
    private val _uiState = MutableStateFlow(CategoryTrendUiState())
    val uiState: StateFlow<CategoryTrendUiState> = _uiState

    init { repository.startListening() }

    fun toggleAvgVisibility() {
        _uiState.update { it.copy(isAvgVisible = !it.isAvgVisible) }
    }

    fun loadData(targetMode: String, baseTimeMillis: Long) {
        viewModelScope.launch {
            repository.transactions.collect { allTx ->
                val cal = Calendar.getInstance().apply { timeInMillis = baseTimeMillis }
                val targetMonth = cal.get(Calendar.MONTH)
                val targetYear = cal.get(Calendar.YEAR)
                val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

                val isExpense = targetMode == "ALL_EXPENSE" || allTx.find { it.categoryName == targetMode }?.type == "EXPENSE"

                val currentMonthTx = allTx.filter { tx ->
                    val txCal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
                    val isSameMonth = txCal.get(Calendar.MONTH) == targetMonth && txCal.get(Calendar.YEAR) == targetYear
                    
                    if (!isSameMonth) return@filter false
                    
                    when (targetMode) {
                        "ALL_EXPENSE" -> tx.type == "EXPENSE" || tx.type == "RECEIVABLE"
                        "ALL_INCOME" -> tx.type == "INCOME" || tx.type == "DEBT"
                        else -> tx.categoryName == targetMode
                    }
                }

                val total = currentMonthTx.sumOf { it.amount }
                val avg = if (daysInMonth > 0) total / daysInMonth else 0.0

                var sum3Month = 0.0
                for (i in 1..3) {
                    val pastCal = Calendar.getInstance().apply { 
                        timeInMillis = baseTimeMillis 
                        add(Calendar.MONTH, -i)
                    }
                    val pM = pastCal.get(Calendar.MONTH)
                    val pY = pastCal.get(Calendar.YEAR)
                    
                    val pastTotal = allTx.filter { tx ->
                        val tCal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
                        tCal.get(Calendar.MONTH) == pM && tCal.get(Calendar.YEAR) == pY
                    }.filter { tx ->
                        when (targetMode) {
                            "ALL_EXPENSE" -> tx.type == "EXPENSE" || tx.type == "RECEIVABLE"
                            "ALL_INCOME" -> tx.type == "INCOME" || tx.type == "DEBT"
                            else -> tx.categoryName == targetMode
                        }
                    }.sumOf { it.amount }
                    sum3Month += pastTotal
                }
                val avg3 = sum3Month / 3.0
                val diffAvg = total - avg3

                val isGlobalMode = targetMode == "ALL_EXPENSE" || targetMode == "ALL_INCOME"
                val breakdownMap = if (isGlobalMode) {
                    currentMonthTx.groupBy { it.categoryName }
                } else {
                    currentMonthTx.groupBy { it.note.ifBlank { "Tanpa Catatan" } }
                }

                val breakdownList = breakdownMap.mapValues { it.value.sumOf { tx -> tx.amount } }
                    .toList()
                    .sortedByDescending { it.second }
                    .map { (name, amt) ->
                        TrendItem(name, amt, if (total > 0) ((amt / total) * 100).toInt() else 0)
                    }
                val donutVals = breakdownList.map { it.amount.toFloat() }

                val trendList = mutableListOf<TrendItem>()
                val barVals = mutableListOf<Float>()
                val partitions = listOf(1..7, 8..14, 15..21, 22..daysInMonth)
                
                for (range in partitions) {
                    val amtInWeek = currentMonthTx.filter { tx ->
                        val d = Calendar.getInstance().apply { timeInMillis = tx.timestamp }.get(Calendar.DAY_OF_MONTH)
                        d in range
                    }.sumOf { it.amount }
                    
                    val label = "${String.format("%02d", range.first)} - ${String.format("%02d", range.last)}"
                    trendList.add(TrendItem(label, amtInWeek))
                    barVals.add(amtInWeek.toFloat())
                }

                val title = when (targetMode) {
                    "ALL_EXPENSE" -> "Rincian Biaya"
                    "ALL_INCOME" -> "Rincian Pendapatan"
                    else -> targetMode
                }

                _uiState.value = CategoryTrendUiState(
                    targetName = title,
                    targetMode = targetMode,
                    isExpenseMode = isExpense,
                    totalAmount = total,
                    dailyAverage = avg,
                    avg3Month = avg3,
                    diffFromAvg = diffAvg,
                    isAvgVisible = _uiState.value.isAvgVisible,
                    timeNavItems = generateTimeNav(baseTimeMillis),
                    selectedTimeMillis = baseTimeMillis,
                    breakdownItems = breakdownList,
                    donutValues = donutVals,
                    trendItems = trendList,
                    trendBarValues = barVals
                )
            }
        }
    }

    // 🔥 FIX: Mesin Waktu yang Selalu Akurat Relatif Terhadap "Sekarang"
    private fun generateTimeNav(selectedTimeMillis: Long): List<TimeNavItem> {
        val list = mutableListOf<TimeNavItem>()
        val realNow = Calendar.getInstance()
        val realMonth = realNow.get(Calendar.MONTH)
        val realYear = realNow.get(Calendar.YEAR)

        val selCal = Calendar.getInstance().apply { timeInMillis = selectedTimeMillis }
        val selMonth = selCal.get(Calendar.MONTH)
        val selYear = selCal.get(Calendar.YEAR)

        // Cari tahu seberapa jauh user melompat ke belakang
        val diffYears = realYear - selYear
        val diffMonths = (diffYears * 12) + (realMonth - selMonth)
        
        // Buat daftar statis sebanyak 24 bulan ke belakang. 
        // Jika user melihat data 3 tahun lalu (diffMonths > 24), listnya diperpanjang otomatis
        val totalTabs = maxOf(24, diffMonths + 6) 

        val iterCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, -(totalTabs - 1))
        }
        
        for (i in 0 until totalTabs) {
            val m = iterCal.get(Calendar.MONTH)
            val y = iterCal.get(Calendar.YEAR)
            
            val label = when {
                m == realMonth && y == realYear -> "BULAN INI"
                m == (realMonth - 1 + 12) % 12 && (if(realMonth==0) y==realYear-1 else y==realYear) -> "BULAN LALU"
                else -> "${String.format("%02d", m + 1)}/$y"
            }
            
            val isSelected = (m == selMonth && y == selYear)
            list.add(TimeNavItem(label, iterCal.timeInMillis, isSelected))
            
            iterCal.add(Calendar.MONTH, 1)
        }
        return list
    }
}
