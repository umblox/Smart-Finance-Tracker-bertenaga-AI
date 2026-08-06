package com.smartfinance.tracker.ui.budget

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.smartfinance.tracker.R
import com.smartfinance.tracker.data.model.Category
import com.smartfinance.tracker.data.repository.BudgetRepository
import com.smartfinance.tracker.data.repository.CategoryRepository
import com.smartfinance.tracker.data.repository.TransactionRepository
import java.util.HashMap

// 🔥 FIX: Upgrade ke AndroidViewModel agar aman mengakses String Resources
class BudgetViewModel(application: Application) : AndroidViewModel(application) {
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

    suspend fun validateAndSaveBudget(docId: String?, limitAmountStr: String, category: Category?) {
        val app = getApplication<Application>()
        
        // 🔥 FIX: Menggunakan string dinamis yang mendukung dwibahasa
        if (category == null) throw Exception(app.getString(R.string.budget_err_category))
        if (limitAmountStr.isBlank()) throw Exception(app.getString(R.string.budget_err_empty_limit))
        
        val limitAmount = limitAmountStr.toDoubleOrNull() ?: 0.0
        if (limitAmount <= 0) throw Exception(app.getString(R.string.budget_err_invalid_amount))

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
