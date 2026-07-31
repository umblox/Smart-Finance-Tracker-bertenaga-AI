package com.smartfinance.tracker.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfinance.tracker.data.model.Transaction
import com.smartfinance.tracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class CategoryAnalyticsUiState(
    val categoryName: String = "",
    val timeLabel: String = "",
    val totalSpent: Double = 0.0,
    val transactions: List<Transaction> = emptyList(),
    val isEmpty: Boolean = true
)

class CategoryAnalyticsViewModel : ViewModel() {
    private val repository = TransactionRepository()
    private val _uiState = MutableStateFlow(CategoryAnalyticsUiState())
    val uiState: StateFlow<CategoryAnalyticsUiState> = _uiState

    init { repository.startListening() }

    // 🔥 FIX: Tambahkan parameter dayRange dan noteFilter untuk menerima instruksi dari Level 3
    fun loadCategoryData(categoryName: String, timeFilterString: String, baseTimeMillis: Long, dayRange: String? = null, noteFilter: String? = null) {
        viewModelScope.launch {
            repository.transactions.collect { allTx ->
                val timeFilter = try { TimeFilter.valueOf(timeFilterString) } catch (e: Exception) { TimeFilter.MONTHLY }
                val timeRange = getTimeRange(timeFilter, baseTimeMillis)
                
                // 1. Filter Rentang Waktu Dasar (Bulan/Minggu/Hari ini)
                var filteredTx = allTx.filter { tx ->
                    tx.timestamp in timeRange.first..timeRange.second
                }

                // 🔥 2. Filter Lanjutan: Potongan Hari (Contoh: "27/07 - 31/07" atau "27 - 31")
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
                    } catch (e: Exception) { /* Abaikan jika format gagal, gunakan rentang dasar */ }
                }

                // 🔥 3.A Filter Kategori Spesial (Menangani perintah dari Dashboard & Net Income)
                filteredTx = when (categoryName) {
                    "ALL_NET_INCOME" -> filteredTx // Ambil semua
                    "Rincian Biaya" -> filteredTx.filter { it.type == "EXPENSE" || it.type == "RECEIVABLE" }
                    "Rincian Pendapatan" -> filteredTx.filter { it.type == "INCOME" || it.type == "DEBT" }
                    else -> filteredTx.filter { it.categoryName == categoryName }
                }

                // 🔥 3.B Filter Sub-Kategori / Catatan (Jika diakses dari Level 3)
                if (noteFilter != null) {
                    filteredTx = filteredTx.filter { it.note.ifBlank { "Tanpa Catatan" } == noteFilter }
                }
                
                filteredTx = filteredTx.sortedByDescending { it.timestamp }

                // 4. Hitung Total Sesuai Konteks
                val total = if (categoryName == "ALL_NET_INCOME") {
                    val inc = filteredTx.filter { it.type == "INCOME" || it.type == "DEBT" }.sumOf { it.amount }
                    val exp = filteredTx.filter { it.type == "EXPENSE" || it.type == "RECEIVABLE" }.sumOf { it.amount }
                    inc - exp
                } else {
                    filteredTx.sumOf { it.amount }
                }
                
                val label = dayRange ?: when (timeFilter) {
                    TimeFilter.DAILY -> "Hari Ini"
                    TimeFilter.WEEKLY -> "Minggu Ini"
                    TimeFilter.MONTHLY -> "Bulan Ini"
                }

                _uiState.value = CategoryAnalyticsUiState(
                    categoryName = categoryName,
                    timeLabel = label,
                    totalSpent = total,
                    transactions = filteredTx,
                    isEmpty = filteredTx.isEmpty()
                )
            }
        }
    }

    private fun getTimeRange(filter: TimeFilter, timeMillis: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = timeMillis }
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

    override fun onCleared() {
        super.onCleared()
        repository.stopListening()
    }
}
