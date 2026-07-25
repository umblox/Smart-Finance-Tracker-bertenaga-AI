package com.smartfinance.tracker.ui.budget

import androidx.lifecycle.ViewModel
import com.smartfinance.tracker.data.model.Category
import com.smartfinance.tracker.data.repository.BudgetRepository
import com.smartfinance.tracker.data.repository.CategoryRepository
import com.smartfinance.tracker.data.repository.TransactionRepository
import java.util.HashMap

class BudgetViewModel : ViewModel() {
    private val budgetRepo = BudgetRepository()
    private val txRepo = TransactionRepository()
    private val catRepo = CategoryRepository()

    val budgets = budgetRepo.budgets
    val transactions = txRepo.transactions
    val categories = catRepo.categories

    init {
        budgetRepo.startListening()
        txRepo.startListening()
        catRepo.startListening()
    }

    // 🔥 LOGIKA BISNIS: Validasi & Pembuatan Data
    suspend fun validateAndSaveBudget(docId: String?, limitAmountStr: String, category: Category?) {
        if (category == null) throw Exception("Pilih kategori terlebih dahulu!")
        if (limitAmountStr.isBlank()) throw Exception("Mohon isi nominal batas anggaran!")
        
        val limitAmount = limitAmountStr.toDoubleOrNull() ?: 0.0
        if (limitAmount <= 0) throw Exception("Nominal tidak valid!")

        val data = HashMap<String, Any>().apply {
            put("categoryId", category.id)
            put("categoryName", category.name)
            put("limitAmount", limitAmount)
        }
        
        budgetRepo.saveBudget(docId, data)
    }

    suspend fun deleteBudget(docId: String) {
        budgetRepo.deleteBudget(docId)
    }

    override fun onCleared() {
        super.onCleared()
        budgetRepo.stopListening()
        txRepo.stopListening()
        catRepo.stopListening()
    }
}
