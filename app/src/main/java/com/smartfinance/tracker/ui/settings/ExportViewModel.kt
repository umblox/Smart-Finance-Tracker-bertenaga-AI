package com.smartfinance.tracker.ui.settings

import androidx.lifecycle.ViewModel
import com.smartfinance.tracker.data.model.Transaction
import com.smartfinance.tracker.data.repository.TransactionRepository
import java.util.Calendar

// Struktur data untuk filter
enum class ExportTimeRange { ALL, DAILY, WEEKLY, MONTHLY }
enum class ExportType { ALL, INCOME_ONLY, EXPENSE_ONLY }

class ExportViewModel : ViewModel() {
    private val repository = TransactionRepository()

    init {
        // Nyalakan aliran data saat ViewModel dipanggil
        repository.startListening()
    }

    fun getFilteredTransactions(timeRange: ExportTimeRange, txType: ExportType): List<Transaction> {
        val allTx = repository.transactions.value
        if (allTx.isEmpty()) return emptyList()

        val now = Calendar.getInstance()

        // 1. Saring Berdasarkan Rentang Waktu
        val timeFiltered = allTx.filter { tx ->
            val txCal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
            when (timeRange) {
                ExportTimeRange.ALL -> true
                ExportTimeRange.DAILY -> {
                    txCal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) &&
                    txCal.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                }
                ExportTimeRange.WEEKLY -> {
                    txCal.get(Calendar.WEEK_OF_YEAR) == now.get(Calendar.WEEK_OF_YEAR) &&
                    txCal.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                }
                ExportTimeRange.MONTHLY -> {
                    txCal.get(Calendar.MONTH) == now.get(Calendar.MONTH) &&
                    txCal.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                }
            }
        }

        // 2. Saring Berdasarkan Tipe Transaksi
        return timeFiltered.filter { tx ->
            val isIncome = tx.type == "INCOME" || tx.type == "DEBT"
            val isExpense = tx.type == "EXPENSE" || tx.type == "RECEIVABLE"
            
            when (txType) {
                ExportType.ALL -> true
                ExportType.INCOME_ONLY -> isIncome
                ExportType.EXPENSE_ONLY -> isExpense
            }
        }.sortedByDescending { it.timestamp } // Urutkan dari yang paling baru
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopListening()
    }
}
