package com.smartfinance.tracker.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.smartfinance.tracker.R
import com.smartfinance.tracker.data.model.Category
import com.smartfinance.tracker.data.model.RecurringTransaction
import com.smartfinance.tracker.data.repository.CategoryRepository
import com.smartfinance.tracker.data.repository.RecurringTxRepository
import kotlinx.coroutines.flow.StateFlow
import java.util.HashMap

// 🔥 FIX: Mengubah ViewModel menjadi AndroidViewModel agar bisa mengakses getString() dengan aman
class RecurringTxViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RecurringTxRepository()
    private val categoryRepository = CategoryRepository()

    val schedules: StateFlow<List<RecurringTransaction>> = repository.schedules
    val categories: StateFlow<List<Category>> = categoryRepository.categories

    init {
        repository.startListening()
        categoryRepository.startListening()
    }

    suspend fun validateAndSaveSchedule(
        docId: String?,
        note: String,
        amountStr: String,
        category: Category?,
        contactName: String,
        interval: String,
        nextExecutionTime: Long,
        hasEndDate: Boolean,
        endDate: Long?
    ) {
        // 🔥 FIX: Error ditangkap menggunakan String Resources yang mendukung Dwibahasa
        if (category == null) throw Exception(getApplication<Application>().getString(R.string.recurring_err_category))
        if (note.isBlank() || amountStr.isBlank()) throw Exception(getApplication<Application>().getString(R.string.recurring_err_empty))
        
        val amount = amountStr.toDoubleOrNull() ?: 0.0
        if (amount <= 0) throw Exception(getApplication<Application>().getString(R.string.recurring_err_amount))

        val data = HashMap<String, Any?>().apply {
            put("note", note)
            put("amount", amount)
            put("type", category.type)
            put("categoryId", category.id)
            put("categoryName", category.name)
            put("contactName", contactName.trim())
            put("interval", interval)
            put("nextExecutionTime", nextExecutionTime)
            put("hasEndDate", hasEndDate)
            put("endDate", if (hasEndDate) endDate else null)
            put("isActive", true)
        }

        repository.saveSchedule(docId, data)
    }

    suspend fun deleteSchedule(docId: String) {
        repository.deleteSchedule(docId)
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopListening()
        categoryRepository.stopListening()
    }
}
