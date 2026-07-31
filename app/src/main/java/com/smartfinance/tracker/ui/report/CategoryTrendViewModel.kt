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

data class TrendItem(val label: String, val amount: Double, val percentage: Int = 0)
data class TimeNavItem(val label: String, val timeMillis: Long, val isSelected: Boolean)

data class CategoryTrendUiState(
    val targetName: String = "Rincian Biaya",
    val targetMode: String = "ALL_EXPENSE",
    val targetType: String = "GLOBAL", // 🔥 BARU: GLOBAL, CATEGORY, atau NOTE
    val parentCategory: String = "",   // 🔥 BARU: Untuk mengingat kategori induk saat di mode NOTE
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
    val trendBarValues: List<Float> = emptyList(),
    val availableCategories: List<String> = emptyList()
)

class CategoryTrendViewModel : ViewModel() {
    private val repository = TransactionRepository()
    private val _uiState = MutableStateFlow(CategoryTrendUiState())
    val uiState: StateFlow<CategoryTrendUiState> = _uiState

    init { repository.startListening() }

    fun toggleAvgVisibility() { _uiState.update { it.copy(isAvgVisible = !it.isAvgVisible) } }

    fun loadData(initialTargetMode: String, targetType: String, baseTimeMillis: Long, parentCategory: String = "") {
        viewModelScope.launch {
            repository.transactions.collect { allTx ->
                val cal = Calendar.getInstance().apply { timeInMillis = baseTimeMillis }
                val targetMonth = cal.get(Calendar.MONTH)
                val targetYear = cal.get(Calendar.YEAR)
                val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

                val currentMonthTxAll = allTx.filter { tx ->
                    val txCal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
                    txCal.get(Calendar.MONTH) == targetMonth && txCal.get(Calendar.YEAR) == targetYear
                }

                var actualTargetMode = initialTargetMode
                if (initialTargetMode == "AUTO_TOP_EXPENSE") {
                    val topCat = currentMonthTxAll.filter { it.type == "EXPENSE" || it.type == "RECEIVABLE" }
                        .groupBy { it.categoryName }.mapValues { it.value.sumOf { tx -> tx.amount } }
                        .maxByOrNull { it.value }?.key
                    actualTargetMode = topCat ?: "ALL_EXPENSE"
                }

                // Tentukan Mode Pemasukan/Pengeluaran
                val isExpense = if (targetType == "GLOBAL") actualTargetMode == "ALL_EXPENSE" 
                                else allTx.find { it.categoryName == (if (targetType == "NOTE") parentCategory else actualTargetMode) }?.type == "EXPENSE"

                val availableCategoriesList = currentMonthTxAll.filter { tx ->
                    if (isExpense) (tx.type == "EXPENSE" || tx.type == "RECEIVABLE") else (tx.type == "INCOME" || tx.type == "DEBT")
                }.map { it.categoryName }.distinct().sorted()

                // 🔥 LOGIKA FILTERING 3 LEVEL
                val currentMonthTx = currentMonthTxAll.filter { tx ->
                    when (targetType) {
                        "GLOBAL" -> if (actualTargetMode == "ALL_EXPENSE") tx.type == "EXPENSE" || tx.type == "RECEIVABLE" else tx.type == "INCOME" || tx.type == "DEBT"
                        "CATEGORY" -> tx.categoryName == actualTargetMode
                        "NOTE" -> tx.categoryName == parentCategory && tx.note.ifBlank { "Tanpa Catatan" } == actualTargetMode
                        else -> false
                    }
                }

                val total = currentMonthTx.sumOf { it.amount }
                val avg = if (daysInMonth > 0) total / daysInMonth else 0.0

                // Hitung Rata-rata 3 Bulan (Sesuai Konteks Level)
                var sum3Month = 0.0
                for (i in 1..3) {
                    val pastCal = Calendar.getInstance().apply { timeInMillis = baseTimeMillis; add(Calendar.MONTH, -i) }
                    val pM = pastCal.get(Calendar.MONTH)
                    val pY = pastCal.get(Calendar.YEAR)
                    sum3Month += allTx.filter { tx ->
                        val tCal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
                        tCal.get(Calendar.MONTH) == pM && tCal.get(Calendar.YEAR) == pY
                    }.filter { tx ->
                        when (targetType) {
                            "GLOBAL" -> if (actualTargetMode == "ALL_EXPENSE") tx.type == "EXPENSE" || tx.type == "RECEIVABLE" else tx.type == "INCOME" || tx.type == "DEBT"
                            "CATEGORY" -> tx.categoryName == actualTargetMode
                            "NOTE" -> tx.categoryName == parentCategory && tx.note.ifBlank { "Tanpa Catatan" } == actualTargetMode
                            else -> false
                        }
                    }.sumOf { it.amount }
                }
                val avg3 = sum3Month / 3.0

                // Breakdown Logic (Hanya untuk Level 1 & 2)
                val breakdownMap = when (targetType) {
                    "GLOBAL" -> currentMonthTx.groupBy { it.categoryName }
                    "CATEGORY" -> currentMonthTx.groupBy { it.note.ifBlank { "Tanpa Catatan" } }
                    else -> emptyMap() // Level 3 tidak butuh donat pie chart
                }

                val breakdownList = breakdownMap.mapValues { it.value.sumOf { tx -> tx.amount } }.toList()
                    .sortedByDescending { it.second }
                    .map { (name, amt) -> TrendItem(name, amt, if (total > 0) ((amt / total) * 100).toInt() else 0) }
                val donutVals = breakdownList.map { it.amount.toFloat() }

                // Trend Logic (Bar Chart Mingguan)
                val trendList = mutableListOf<TrendItem>()
                val barVals = mutableListOf<Float>()
                val partitions = listOf(1..7, 8..14, 15..21, 22..daysInMonth)
                
                for (range in partitions) {
                    val amtInWeek = currentMonthTx.filter { tx ->
                        val d = Calendar.getInstance().apply { timeInMillis = tx.timestamp }.get(Calendar.DAY_OF_MONTH)
                        d in range
                    }.sumOf { it.amount }
                    trendList.add(TrendItem("${String.format("%02d", range.first)}/${String.format("%02d", targetMonth + 1)} - ${String.format("%02d", range.last)}/${String.format("%02d", targetMonth + 1)}", amtInWeek))
                    barVals.add(amtInWeek.toFloat())
                }

                val title = if (targetType == "GLOBAL") (if (actualTargetMode == "ALL_EXPENSE") "Rincian Biaya" else "Rincian Pendapatan") else actualTargetMode

                _uiState.value = CategoryTrendUiState(
                    targetName = title, targetMode = actualTargetMode, targetType = targetType, parentCategory = parentCategory,
                    isExpenseMode = isExpense, totalAmount = total, dailyAverage = avg, avg3Month = avg3, diffFromAvg = total - avg3,
                    isAvgVisible = _uiState.value.isAvgVisible, timeNavItems = generateTimeNav(baseTimeMillis),
                    selectedTimeMillis = baseTimeMillis, breakdownItems = breakdownList, donutValues = donutVals,
                    trendItems = trendList, trendBarValues = barVals, availableCategories = availableCategoriesList
                )
            }
        }
    }

    private fun generateTimeNav(selectedTimeMillis: Long): List<TimeNavItem> {
        val list = mutableListOf<TimeNavItem>()
        val realNow = Calendar.getInstance()
        val selCal = Calendar.getInstance().apply { timeInMillis = selectedTimeMillis }
        val diffMonths = ((realNow.get(Calendar.YEAR) - selCal.get(Calendar.YEAR)) * 12) + (realNow.get(Calendar.MONTH) - selCal.get(Calendar.MONTH))
        val totalTabs = maxOf(24, diffMonths + 6) 
        val iterCal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, -(totalTabs - 1)) }
        
        for (i in 0 until totalTabs) {
            val m = iterCal.get(Calendar.MONTH)
            val y = iterCal.get(Calendar.YEAR)
            val label = when {
                m == realNow.get(Calendar.MONTH) && y == realNow.get(Calendar.YEAR) -> "BULAN INI"
                m == (realNow.get(Calendar.MONTH) - 1 + 12) % 12 && (if(realNow.get(Calendar.MONTH)==0) y==realNow.get(Calendar.YEAR)-1 else y==realNow.get(Calendar.YEAR)) -> "BULAN LALU"
                else -> "${String.format("%02d", m + 1)}/$y"
            }
            list.add(TimeNavItem(label, iterCal.timeInMillis, (m == selCal.get(Calendar.MONTH) && y == selCal.get(Calendar.YEAR))))
            iterCal.add(Calendar.MONTH, 1)
        }
        return list
    }
}
