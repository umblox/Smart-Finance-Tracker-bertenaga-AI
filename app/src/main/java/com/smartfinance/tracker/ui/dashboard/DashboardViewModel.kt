package com.smartfinance.tracker.ui.dashboard

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
import java.util.Locale

data class DashboardUiState(
    val totalBalance: Double = 0.0,
    val incomeThisMonth: Double = 0.0,
    val expenseThisMonth: Double = 0.0,
    val incomeLastMonth: Double = 0.0,
    val expenseLastMonth: Double = 0.0,
    val topExpenses: List<Pair<String, Double>> = emptyList(),
    val topExpensesTotal: Double = 0.0,
    val recentTransactions: List<Transaction> = emptyList(),
    val categoryIconMap: Map<String, String> = emptyMap(), 
    val activeTimeLabel: String = ""
)

// 🔥 FIX: Upgrade ke AndroidViewModel agar bisa mengakses strings.xml dengan aman
class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TransactionRepository()
    private val catRepo = CategoryRepository() 

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState

    // 🔥 FIX: Ambil default filter dari string resources
    private var currentTopFilter = application.getString(R.string.dashboard_filter_this_month)
    private var activeTimePrefs = System.currentTimeMillis()
    private var latestCategories = emptyList<Category>()

    init {
        repository.startListening()
        catRepo.startListening()
        
        viewModelScope.launch {
            combine(repository.transactions, catRepo.categories) { txs, cats ->
                Pair(txs, cats)
            }.collect { (txs, cats) ->
                latestCategories = cats
                calculateDashboard(txs)
            }
        }
    }

    fun updatePreferences(time: Long, filter: String) {
        activeTimePrefs = time
        currentTopFilter = filter
        calculateDashboard(repository.transactions.value)
    }

    private fun calculateDashboard(allTx: List<Transaction>) {
        val calToday = Calendar.getInstance().apply { timeInMillis = activeTimePrefs }
        val calLastMonth = Calendar.getInstance().apply { timeInMillis = activeTimePrefs; add(Calendar.MONTH, -1) }

        var balanceTotal = 0.0; var incomeThisMonth = 0.0; var expenseThisMonth = 0.0
        var incomeLastMonth = 0.0; var expenseLastMonth = 0.0

        allTx.forEach { tx ->
            if (tx.type == "INCOME" || tx.type == "DEBT") balanceTotal += tx.amount
            else if (tx.type == "EXPENSE" || tx.type == "RECEIVABLE") balanceTotal -= tx.amount

            val txCal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
            val isThisMonth = txCal.get(Calendar.MONTH) == calToday.get(Calendar.MONTH) && txCal.get(Calendar.YEAR) == calToday.get(Calendar.YEAR)
            val isLastMonth = txCal.get(Calendar.MONTH) == calLastMonth.get(Calendar.MONTH) && txCal.get(Calendar.YEAR) == calLastMonth.get(Calendar.YEAR)

            if (isThisMonth) {
                if (tx.type == "INCOME" || tx.type == "DEBT") incomeThisMonth += tx.amount
                if (tx.type == "EXPENSE" || tx.type == "RECEIVABLE") expenseThisMonth += tx.amount
            }
            if (isLastMonth) {
                if (tx.type == "INCOME" || tx.type == "DEBT") incomeLastMonth += tx.amount
                if (tx.type == "EXPENSE" || tx.type == "RECEIVABLE") expenseLastMonth += tx.amount
            }
        }

        val nowTime = System.currentTimeMillis()
        val strWeekly = getApplication<Application>().getString(R.string.dashboard_filter_weekly)

        val filteredExpenses = allTx.filter { it.type == "EXPENSE" || it.type == "RECEIVABLE" }
            .filter { tx ->
                // 🔥 FIX: Menggunakan string dinamis untuk filter perminggu
                if (currentTopFilter == strWeekly) {
                    (nowTime - tx.timestamp) <= (7L * 24 * 60 * 60 * 1000)
                } else {
                    val t = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
                    t.get(Calendar.MONTH) == calToday.get(Calendar.MONTH) && t.get(Calendar.YEAR) == calToday.get(Calendar.YEAR)
                }
            }

        val totalFilteredExpenseAmount = filteredExpenses.sumOf { it.amount }
        val aggregatedExpenses = filteredExpenses.groupBy { it.categoryName }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList().sortedByDescending { it.second }.take(3)

        val recentTxList = allTx.sortedByDescending { it.timestamp }.take(4)
        
        // 🔥 FIX: Menggunakan Locale.getDefault() agar bulan beradaptasi otomatis
        val sdfMonthLabel = java.text.SimpleDateFormat("MMMM", Locale.getDefault())
        val iconMap = latestCategories.associate { it.name to it.iconName }

        _uiState.value = DashboardUiState(
            totalBalance = balanceTotal, incomeThisMonth = incomeThisMonth,
            expenseThisMonth = expenseThisMonth, incomeLastMonth = incomeLastMonth,
            expenseLastMonth = expenseLastMonth, topExpenses = aggregatedExpenses,
            topExpensesTotal = totalFilteredExpenseAmount, recentTransactions = recentTxList,
            categoryIconMap = iconMap, activeTimeLabel = sdfMonthLabel.format(calToday.time)
        )
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopListening()
        catRepo.stopListening()
    }
}
