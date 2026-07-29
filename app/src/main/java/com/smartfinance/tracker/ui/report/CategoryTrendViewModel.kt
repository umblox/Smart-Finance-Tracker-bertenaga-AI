package com.smartfinance.tracker.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfinance.tracker.data.model.Transaction
import com.smartfinance.tracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class TrendItem(
    val label: String,
    val amount: Double,
    val percentage: Int = 0 // Digunakan untuk breakdown
)

data class CategoryTrendUiState(
    val targetName: String = "Rincian Biaya",
    val isExpenseMode: Boolean = true,
    val totalAmount: Double = 0.0,
    val dailyAverage: Double = 0.0,
    
    // Data Tab Breakdown
    val breakdownItems: List<TrendItem> = emptyList(),
    val donutValues: List<Float> = emptyList(),
    
    // Data Tab Trend (dibagi 4 minggu)
    val trendItems: List<TrendItem> = emptyList(),
    val trendBarValues: List<Float> = emptyList()
)

class CategoryTrendViewModel : ViewModel() {
    private val repository = TransactionRepository()
    private val _uiState = MutableStateFlow(CategoryTrendUiState())
    val uiState: StateFlow<CategoryTrendUiState> = _uiState

    init { repository.startListening() }

    // Parameter: "ALL_EXPENSE", "ALL_INCOME", atau nama kategori spesifik ("Pertamax")
    fun loadData(targetMode: String, baseTimeMillis: Long) {
        viewModelScope.launch {
            repository.transactions.collect { allTx ->
                val cal = Calendar.getInstance().apply { timeInMillis = baseTimeMillis }
                val targetMonth = cal.get(Calendar.MONTH)
                val targetYear = cal.get(Calendar.YEAR)
                val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

                val isExpense = targetMode == "ALL_EXPENSE" || allTx.find { it.categoryName == targetMode }?.type == "EXPENSE"
                
                // 1. Filter transaksi bulan ini sesuai target
                val filteredTx = allTx.filter { tx ->
                    val txCal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
                    val isSameMonth = txCal.get(Calendar.MONTH) == targetMonth && txCal.get(Calendar.YEAR) == targetYear
                    
                    if (!isSameMonth) return@filter false
                    
                    when (targetMode) {
                        "ALL_EXPENSE" -> tx.type == "EXPENSE" || tx.type == "RECEIVABLE"
                        "ALL_INCOME" -> tx.type == "INCOME" || tx.type == "DEBT"
                        else -> tx.categoryName == targetMode
                    }
                }

                val total = filteredTx.sumOf { it.amount }
                val avg = if (daysInMonth > 0) total / daysInMonth else 0.0

                // 2. Olah Data untuk Tab BREAKDOWN (Kelompokkan by Kategori)
                val breakdownMap = filteredTx.groupBy { it.categoryName }
                    .mapValues { it.value.sumOf { tx -> tx.amount } }
                    .toList()
                    .sortedByDescending { it.second }
                
                val breakdownList = breakdownMap.map { (name, amt) ->
                    TrendItem(name, amt, if (total > 0) ((amt / total) * 100).toInt() else 0)
                }
                val donutVals = breakdownList.map { it.amount.toFloat() }

                // 3. Olah Data untuk Tab TREND (Kelompokkan by Minggu dalam sebulan)
                val trendList = mutableListOf<TrendItem>()
                val barVals = mutableListOf<Float>()
                
                // Bikin 4 partisi (Contoh: Tgl 1-7, 8-14, 15-21, 22-Akhir)
                val partitions = listOf(1..7, 8..14, 15..21, 22..daysInMonth)
                
                for (range in partitions) {
                    val amtInWeek = filteredTx.filter { tx ->
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
                    isExpenseMode = isExpense,
                    totalAmount = total,
                    dailyAverage = avg,
                    breakdownItems = breakdownList,
                    donutValues = donutVals,
                    trendItems = trendList,
                    trendBarValues = barVals
                )
            }
        }
    }
}

