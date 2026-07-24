package com.smartfinance.tracker.ui.budget

import androidx.lifecycle.ViewModel
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

    suspend fun saveBudget(docId: String?, data: HashMap<String, Any>) {
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
