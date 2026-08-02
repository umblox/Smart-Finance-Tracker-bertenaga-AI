package com.smartfinance.tracker.ui.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfinance.tracker.data.model.Category
import com.smartfinance.tracker.data.model.Transaction
import com.smartfinance.tracker.data.repository.CategoryRepository
import com.smartfinance.tracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.collections.LinkedHashMap

data class HistoryUiState(
    val currentMonthLabel: String = "",
    val groupedTransactions: LinkedHashMap<String, List<Transaction>> = LinkedHashMap(),
    val categoryIconMap: Map<String, String> = emptyMap(), // 🔥 MAP IKON
    val isEmpty: Boolean = true
)

class HistoryViewModel : ViewModel() {
    private val repository = TransactionRepository()
    private val catRepo = CategoryRepository()
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState

    private var currentCalendar = Calendar.getInstance()
    private val sdfLabel = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))
    private val sdfDay = SimpleDateFormat("EEEE, dd MMM yyyy", Locale("id", "ID"))
    
    private var latestCategories = emptyList<Category>()

    init {
        repository.startListening()
        catRepo.startListening()
        
        viewModelScope.launch {
            combine(repository.transactions, catRepo.categories) { txs, cats ->
                Pair(txs, cats)
            }.collect { (txs, cats) ->
                latestCategories = cats
                processTransactions(txs)
            }
        }
    }

    fun changeMonth(amount: Int) {
        currentCalendar.add(Calendar.MONTH, amount)
        processTransactions(repository.transactions.value)
    }

    private fun processTransactions(allTx: List<Transaction>) {
        val targetMonth = currentCalendar.get(Calendar.MONTH)
        val targetYear = currentCalendar.get(Calendar.YEAR)

        val monthlyList = allTx.filter { tx ->
            val txCal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
            txCal.get(Calendar.MONTH) == targetMonth && txCal.get(Calendar.YEAR) == targetYear
        }.sortedByDescending { it.timestamp }

        val groupMap = LinkedHashMap<String, MutableList<Transaction>>()
        
        monthlyList.forEach { tx ->
            val dayHeaderString = sdfDay.format(Date(tx.timestamp))
            if (!groupMap.containsKey(dayHeaderString)) {
                groupMap[dayHeaderString] = mutableListOf()
            }
            groupMap[dayHeaderString]?.add(tx)
        }
        
        val iconMap = latestCategories.associate { it.name to it.iconName }

        _uiState.value = HistoryUiState(
            currentMonthLabel = sdfLabel.format(currentCalendar.time).uppercase(Locale.ROOT),
            groupedTransactions = groupMap as LinkedHashMap<String, List<Transaction>>,
            categoryIconMap = iconMap,
            isEmpty = monthlyList.isEmpty()
        )
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopListening()
        catRepo.stopListening()
    }
}
