package com.smartfinance.tracker.ui.report

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartfinance.tracker.R
import com.smartfinance.tracker.data.model.Category
import com.smartfinance.tracker.data.model.Transaction
import com.smartfinance.tracker.data.repository.CategoryRepository
import com.smartfinance.tracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar

data class CategoryAnalyticsUiState(
    val categoryName: String = "",
    val timeLabel: String = "",
    val totalIncome: Double = 0.0,  
    val totalExpense: Double = 0.0, 
    val transactions: List<Transaction> = emptyList(),
    val categoryIconMap: Map<String, String> = emptyMap(), 
    val isEmpty: Boolean = true
)

class CategoryAnalyticsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TransactionRepository()
    private val catRepo = CategoryRepository()
    private val _uiState = MutableStateFlow(CategoryAnalyticsUiState())
    val uiState: StateFlow<CategoryAnalyticsUiState> = _uiState

    init { 
        repository.startListening() 
        catRepo.startListening()
    }

    fun loadCategoryData(categoryName: String, timeFilterString: String, baseTimeMillis: Long, dayRange: String? = null, noteFilter: String? = null) {
        viewModelScope.launch {
            combine(repository.transactions, catRepo.categories) { allTx, cats ->
                Pair(allTx, cats)
            }.collect { (allTx, cats) ->
                val app = getApplication<Application>()
                val strExpenseDetails = app.getString(R.string.trend_expense_details)
                val strIncomeDetails = app.getString(R.string.trend_income_details)
                val strNoNote = app.getString(R.string.trend_no_note)

                val timeFilter = try { TimeFilter.valueOf(timeFilterString) } catch (e: Exception) { TimeFilter.MONTHLY }
                val timeRange = getTimeRange(timeFilter, baseTimeMillis)
                
                var filteredTx = allTx.filter { tx -> tx.timestamp in timeRange.first..timeRange.second }

                if (dayRange != null) {
                    try {
                        val parts = dayRange.split("-")
                        if (parts.size == 2) {
                            val startDay = parts[0].trim().substringBefore("/").toIntOrNull() ?: 1
                            val endDay = parts[1].trim().substringBefore("/").toIntOrNull() ?: 31
                            filteredTx = filteredTx.filter { tx ->
                                val d = Calendar.getInstance().apply { timeInMillis = tx.timestamp }.get(Calendar.DAY_OF_MONTH)
                                d in startDay..endDay
                            }
                        }
                    } catch (e: Exception) {}
                }

                filteredTx = when (categoryName) {
                    "ALL_NET_INCOME" -> filteredTx
                    // 🔥 FIX: Menggunakan string dinamis sebagai parameter logika antar layar
                    strExpenseDetails -> filteredTx.filter { it.type == "EXPENSE" || it.type == "RECEIVABLE" }
                    strIncomeDetails -> filteredTx.filter { it.type == "INCOME" || it.type == "DEBT" }
                    
                    // 🔥 FIX: Memperlebar filter default database agar mendukung bahasa Inggris
                    "FILTER_HUTANG" -> filteredTx.filter { it.type == "DEBT" || it.categoryName.equals("Hutang", true) || it.categoryName.equals("Utang", true) || it.categoryName.equals("Debt", true) }
                    "FILTER_PIUTANG" -> filteredTx.filter { it.type == "RECEIVABLE" || it.categoryName.equals("Piutang", true) || it.categoryName.equals("Receivable", true) || it.categoryName.equals("Accounts Receivable", true) }
                    "FILTER_LAINNYA" -> filteredTx.filter { it.categoryName.equals("Penagihan Utang", true) || it.categoryName.equals("Pembayaran kembali", true) || it.categoryName.equals("Debt Collection", true) || it.categoryName.equals("Repayment", true) }
                    
                    else -> filteredTx.filter { it.categoryName == categoryName }
                }

                if (noteFilter != null) {
                    // 🔥 FIX: Menyamakan pencarian string dinamis
                    filteredTx = filteredTx.filter { it.note.ifBlank { strNoNote } == noteFilter }
                }
                
                filteredTx = filteredTx.sortedByDescending { it.timestamp }

                val income = filteredTx.filter { it.type == "INCOME" || it.type == "DEBT" || it.amount > 0 }.sumOf { it.amount }
                val expense = filteredTx.filter { it.type == "EXPENSE" || it.type == "RECEIVABLE" || it.amount < 0 }.sumOf { kotlin.math.abs(it.amount) }
                
                val label = dayRange ?: when (timeFilter) {
                    TimeFilter.DAILY -> app.getString(R.string.analytics_today)
                    TimeFilter.WEEKLY -> app.getString(R.string.analytics_this_week)
                    TimeFilter.MONTHLY -> app.getString(R.string.analytics_this_month)
                }

                val iconMap = cats.associate { it.name to it.iconName }

                _uiState.value = CategoryAnalyticsUiState(
                    categoryName = categoryName,
                    timeLabel = label,
                    totalIncome = income,
                    totalExpense = expense,
                    transactions = filteredTx,
                    categoryIconMap = iconMap,
                    isEmpty = filteredTx.isEmpty()
                )
            }
        }
    }

    private fun getTimeRange(filter: TimeFilter, timeMillis: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = timeMillis }
        val startCal = cal.clone() as Calendar; val endCal = cal.clone() as Calendar
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

    override fun onCleared() { super.onCleared(); repository.stopListening(); catRepo.stopListening() }
}
