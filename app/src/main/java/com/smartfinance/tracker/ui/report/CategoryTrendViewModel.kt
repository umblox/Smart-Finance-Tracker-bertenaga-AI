package com.smartfinance.tracker.ui.report

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartfinance.tracker.R
import com.smartfinance.tracker.data.model.Transaction
import com.smartfinance.tracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

data class TrendItem(val label: String, val amount: Double, val percentage: Int = 0)
data class TimeNavItem(val label: String, val timeMillis: Long, val isSelected: Boolean)

data class CategoryTrendUiState(
    val targetName: String = "",
    val targetMode: String = "ALL_EXPENSE",
    val targetType: String = "GLOBAL", 
    val parentCategory: String = "",   
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

class CategoryTrendViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TransactionRepository()
    private val _uiState = MutableStateFlow(CategoryTrendUiState())
    val uiState: StateFlow<CategoryTrendUiState> = _uiState

    init { repository.startListening() }

    fun toggleAvgVisibility() { _uiState.update { it.copy(isAvgVisible = !it.isAvgVisible) } }

    fun loadData(initialTargetMode: String, targetType: String, baseTimeMillis: Long, parentCategory: String = "") {
        viewModelScope.launch {
            repository.transactions.collect { allTx ->
                val app = getApplication<Application>()
                val strExpenseDetails = app.getString(R.string.trend_expense_details)
                val strIncomeDetails = app.getString(R.string.trend_income_details)
                val strNoCategory = app.getString(R.string.trend_no_category)
                val strNoNote = app.getString(R.string.trend_no_note)

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
                    
                    if (topCat != null) {
                        actualTargetMode = topCat
                    } else {
                        val fallbackCat = allTx.filter { it.type == "EXPENSE" || it.type == "RECEIVABLE" }
                            .map { it.categoryName }.firstOrNull()
                        actualTargetMode = fallbackCat ?: strNoCategory 
                    }
                }

                val isExpense = if (targetType == "GLOBAL") {
                    actualTargetMode == "ALL_EXPENSE"
                } else {
                    val targetCatName = if (targetType == "NOTE") parentCategory else actualTargetMode
                    allTx.find { it.categoryName == targetCatName }?.type != "INCOME"
                }

                val availableCategoriesList = allTx.filter { tx ->
                    if (isExpense) (tx.type == "EXPENSE" || tx.type == "RECEIVABLE") else (tx.type == "INCOME" || tx.type == "DEBT")
                }.map { it.categoryName }.distinct().sorted()

                val currentMonthTx = currentMonthTxAll.filter { tx ->
                    when (targetType) {
                        "GLOBAL" -> if (actualTargetMode == "ALL_EXPENSE") tx.type == "EXPENSE" || tx.type == "RECEIVABLE" else tx.type == "INCOME" || tx.type == "DEBT"
                        "CATEGORY" -> tx.categoryName == actualTargetMode
                        "NOTE" -> tx.categoryName == parentCategory && tx.note.ifBlank { strNoNote } == actualTargetMode
                        else -> false
                    }
                }

                val total = currentMonthTx.sumOf { it.amount }
                val avg = if (daysInMonth > 0) total / daysInMonth else 0.0

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
                            "NOTE" -> tx.categoryName == parentCategory && tx.note.ifBlank { strNoNote } == actualTargetMode
                            else -> false
                        }
                    }.sumOf { it.amount }
                }
                val avg3 = sum3Month / 3.0

                val breakdownMap = when (targetType) {
                    "GLOBAL" -> currentMonthTx.groupBy { it.categoryName }
                    "CATEGORY" -> currentMonthTx.groupBy { it.note.ifBlank { strNoNote } }
                    else -> emptyMap() 
                }

                val breakdownList = breakdownMap.mapValues { it.value.sumOf { tx -> tx.amount } }.toList()
                    .sortedByDescending { it.second }
                    .map { (name, amt) -> TrendItem(name, amt, if (total > 0) ((amt / total) * 100).toInt() else 0) }
                val donutVals = breakdownList.map { it.amount.toFloat() }

                val trendList = mutableListOf<TrendItem>()
                val barVals = mutableListOf<Float>()
                val partitions = listOf(1..7, 8..14, 15..21, 22..daysInMonth)
                
                for (range in partitions) {
                    val amtInWeek = currentMonthTx.filter { tx ->
                        val d = Calendar.getInstance().apply { timeInMillis = tx.timestamp }.get(Calendar.DAY_OF_MONTH)
                        d in range
                    }.sumOf { it.amount }
                    trendList.add(TrendItem("${String.format(Locale.getDefault(), "%02d", range.first)}/${String.format(Locale.getDefault(), "%02d", targetMonth + 1)} - ${String.format(Locale.getDefault(), "%02d", range.last)}/${String.format(Locale.getDefault(), "%02d", targetMonth + 1)}", amtInWeek))
                    barVals.add(amtInWeek.toFloat())
                }

                val title = if (targetType == "GLOBAL") (if (actualTargetMode == "ALL_EXPENSE") strExpenseDetails else strIncomeDetails) else actualTargetMode

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
        val app = getApplication<Application>()
        val strThisMonth = app.getString(R.string.trend_this_month)
        val strLastMonth = app.getString(R.string.trend_last_month)

        val list = mutableListOf<TimeNavItem>()
        val realNow = Calendar.getInstance()
        val realMonth = realNow.get(Calendar.MONTH)
        val realYear = realNow.get(Calendar.YEAR)
        
        val selCal = Calendar.getInstance().apply { timeInMillis = selectedTimeMillis }
        val diffMonths = ((realYear - selCal.get(Calendar.YEAR)) * 12) + (realMonth - selCal.get(Calendar.MONTH))
        val totalTabs = maxOf(24, diffMonths + 6) 
        val iterCal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, -(totalTabs - 1)) }
        
        for (i in 0 until totalTabs) {
            val m = iterCal.get(Calendar.MONTH)
            val y = iterCal.get(Calendar.YEAR)
            val label = when {
                m == realMonth && y == realYear -> strThisMonth
                m == (realMonth - 1 + 12) % 12 && (if(realMonth == 0) y == realYear - 1 else y == realYear) -> strLastMonth
                else -> "${String.format(Locale.getDefault(), "%02d", m + 1)}/$y"
            }
            list.add(TimeNavItem(label, iterCal.timeInMillis, (m == selCal.get(Calendar.MONTH) && y == selCal.get(Calendar.YEAR))))
            iterCal.add(Calendar.MONTH, 1)
        }
        return list
    }
}
