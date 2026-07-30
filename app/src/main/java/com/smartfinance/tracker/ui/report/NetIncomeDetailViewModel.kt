package com.smartfinance.tracker.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfinance.tracker.data.model.Transaction
import com.smartfinance.tracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class NetIncomeChunk(
    val label: String,
    val income: Double,
    val expense: Double,
    val net: Double
)

data class NetIncomeDetailUiState(
    val totalNetIncome: Double = 0.0,
    val chunks: List<NetIncomeChunk> = emptyList()
)

class NetIncomeDetailViewModel : ViewModel() {
    private val repository = TransactionRepository()
    private val _uiState = MutableStateFlow(NetIncomeDetailUiState())
    val uiState: StateFlow<NetIncomeDetailUiState> = _uiState

    init { repository.startListening() }

    fun loadData(baseTimeMillis: Long) {
        viewModelScope.launch {
            repository.transactions.collect { allTx ->
                val cal = Calendar.getInstance().apply { timeInMillis = baseTimeMillis }
                val targetMonth = cal.get(Calendar.MONTH)
                val targetYear = cal.get(Calendar.YEAR)
                val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

                // Filter transaksi bulan ini
                val monthlyTx = allTx.filter { tx ->
                    val txCal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
                    txCal.get(Calendar.MONTH) == targetMonth && txCal.get(Calendar.YEAR) == targetYear
                }

                // Partisi waktu (1-5, 6-12, 13-19, 20-26, 27-Akhir bulan) 
                // Money Lover biasanya membagi bulan menjadi 5 blok
                val partitions = listOf(1..5, 6..12, 13..19, 20..26, 27..daysInMonth)
                val chunkList = mutableListOf<NetIncomeChunk>()
                var grandTotalNet = 0.0

                for (range in partitions) {
                    var inc = 0.0
                    var exp = 0.0
                    
                    monthlyTx.filter { tx ->
                        val d = Calendar.getInstance().apply { timeInMillis = tx.timestamp }.get(Calendar.DAY_OF_MONTH)
                        d in range
                    }.forEach { tx ->
                        if (tx.type == "INCOME" || tx.type == "DEBT") inc += tx.amount
                        if (tx.type == "EXPENSE" || tx.type == "RECEIVABLE") exp += tx.amount
                    }
                    
                    val net = inc - exp
                    grandTotalNet += net
                    val label = "${String.format("%02d", range.first)}/07 - ${String.format("%02d", range.last)}/07"
                    chunkList.add(NetIncomeChunk(label, inc, exp, net))
                }

                _uiState.value = NetIncomeDetailUiState(
                    totalNetIncome = grandTotalNet,
                    chunks = chunkList
                )
            }
        }
    }
}
