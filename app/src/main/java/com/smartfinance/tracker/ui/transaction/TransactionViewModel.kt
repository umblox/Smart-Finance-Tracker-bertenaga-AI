package com.smartfinance.tracker.ui.transaction

import android.content.Context
import androidx.lifecycle.ViewModel
import com.smartfinance.tracker.data.model.Transaction
import com.smartfinance.tracker.data.repository.DebtRepository
import com.smartfinance.tracker.data.repository.TransactionRepository
import com.smartfinance.tracker.data.local.DatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.ArrayList
import java.util.HashMap
import java.util.Locale
import java.util.Calendar

data class ParsedNoteData(val contactName: String, val cleanNote: String)

class TransactionViewModel : ViewModel() {
    private val txRepository = TransactionRepository()
    private val debtRepository = DebtRepository()

    val transactions: StateFlow<List<Transaction>> = txRepository.transactions

    init {
        txRepository.startListening()
    }

    suspend fun getCategoriesForDropdown(): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        val list = ArrayList<Map<String, Any>>()
        val allCats = DatabaseProvider.db.categoryDao().getAllSync()
        for (cat in allCats) {
            val mutableData = HashMap<String, Any>()
            mutableData["id"] = cat.id
            mutableData["name"] = cat.name
            mutableData["type"] = cat.type
            list.add(mutableData)
        }
        list
    }

    // 🔥 LOGIKA MVVM: Mem-parsing teks kotor menjadi rapi (Dipindah dari Editor Dialog)
    fun parseTransactionNote(rawNote: String, isDebt: Boolean): ParsedNoteData {
        var extractedName = ""
        var cleanNoteToShow = rawNote

        if (isDebt) {
            val matchB = Regex("\\(B/\\s*(.*?)\\)$").find(rawNote)
            if (matchB != null) {
                extractedName = matchB.groupValues[1].trim()
                cleanNoteToShow = rawNote.replace(matchB.value, "").trim()
            } else {
                extractedName = rawNote.replace(Regex("\\[.*?\\]"), "").trim()
                if (extractedName.contains("-")) {
                    val parts = extractedName.split("-", limit = 2)
                    extractedName = parts[0].trim()
                    cleanNoteToShow = parts.getOrNull(1)?.trim() ?: rawNote
                } else {
                    val aiPatterns = listOf(
                        "MEMBERIKAN PINJAMAN KEPADA ", "MENERIMA PINJAMAN DARI ",
                        "MEMBAYAR CICILAN UTANG KE ", "MENERIMA CICILAN PIUTANG DARI "
                    )
                    for (pattern in aiPatterns) {
                        if (extractedName.startsWith(pattern)) {
                            extractedName = extractedName.removePrefix(pattern).trim()
                            cleanNoteToShow = rawNote
                            break
                        }
                    }
                }
            }
        } else {
            val match = Regex("\\(B/\\s*(.*?)\\)$").find(rawNote)
            if (match != null) {
                extractedName = match.groupValues[1].trim()
                cleanNoteToShow = rawNote.replace(match.value, "").trim()
            }
        }
        return ParsedNoteData(extractedName, cleanNoteToShow)
    }

    // 🔥 LOGIKA MVVM: Menentukan tipe transaksi berdasarkan Kategori Baku (101-104)
    fun determineTransactionType(categoryId: Long, selectedType: String, isDebt: Boolean): String {
        if (isDebt || categoryId in listOf(101L, 102L, 103L, 104L)) {
            return if (categoryId == 101L || categoryId == 103L) "INCOME" else "EXPENSE"
        }
        return selectedType.uppercase(Locale.ROOT)
    }

    // 🔥 LOGIKA MVVM: Merakit catatan akhir untuk di-save ke Database
    fun formatTransactionNote(rawNote: String, contactName: String, categoryName: String, isDebt: Boolean): String {
        val cleanNote = rawNote.uppercase(Locale.ROOT)
        val cleanContact = contactName.uppercase(Locale.ROOT)
        
        return if (isDebt) {
            "[$categoryName] $cleanContact - $cleanNote"
        } else {
            if (cleanContact.isNotEmpty()) "$cleanNote (B/ $cleanContact)" else cleanNote
        }
    }

    // 🔥 LOGIKA MVVM: Mengecek limit budget (Dipindah dari Manual Dialog)
    suspend fun checkAndTriggerBudgetAlert(context: Context, categoryId: Long, categoryName: String) = withContext(Dispatchers.IO) {
        try {
            val budgetDoc = DatabaseProvider.db.budgetDao().getByCategoryId(categoryId)
            if (budgetDoc != null) {
                val limitAmount = budgetDoc.limitAmount
                if (limitAmount > 0.0) {
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
                    val startOfMonth = cal.timeInMillis

                    val txs = DatabaseProvider.db.transactionDao().getByCategoryId(categoryId)
                    var totalSpent = 0.0 
                    for (tx in txs) {
                        if (tx.timestamp >= startOfMonth && (tx.type == "EXPENSE" || tx.type == "RECEIVABLE")) { 
                            totalSpent += tx.amount
                        }
                    }

                    if (totalSpent >= (limitAmount * 0.8)) {
                        withContext(Dispatchers.Main) {
                            com.smartfinance.tracker.worker.AiWorkerManager.triggerBudgetAlert(
                                context, categoryName, totalSpent, limitAmount
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun saveTransaction(txId: String, txMap: HashMap<String, Any>) {
        txRepository.saveTransaction(txId, txMap)
    }

    suspend fun deleteTransaction(txId: String) {
        txRepository.deleteTransaction(txId)
    }

    suspend fun saveDebt(debtId: String, debtMap: HashMap<String, Any>) {
        debtRepository.saveDebt(debtId, debtMap)
    }

    suspend fun updateDebtFields(debtId: String, contactName: String, amount: Double, type: String, timestamp: Long) {
        debtRepository.updateDebtFields(debtId, mapOf(
            "contactName" to contactName,
            "amount" to amount,
            "remainingAmount" to amount,
            "type" to type,
            "timestamp" to timestamp
        ))
    }

    suspend fun deleteDebt(debtId: String) {
        debtRepository.deleteDebt(debtId)
    }

    override fun onCleared() {
        super.onCleared()
        txRepository.stopListening()
    }
}
