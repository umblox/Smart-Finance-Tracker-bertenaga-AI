package com.smartfinance.tracker.ui.settings

import androidx.lifecycle.ViewModel
import com.smartfinance.tracker.data.model.Category
import com.smartfinance.tracker.data.model.RecurringTransaction
import com.smartfinance.tracker.data.repository.CategoryRepository
import com.smartfinance.tracker.data.repository.RecurringTxRepository
import kotlinx.coroutines.flow.StateFlow
import java.util.HashMap

class RecurringTxViewModel : ViewModel() {
    private val repository = RecurringTxRepository()
    private val categoryRepository = CategoryRepository()

    val schedules: StateFlow<List<RecurringTransaction>> = repository.schedules
    val categories: StateFlow<List<Category>> = categoryRepository.categories

    init {
        repository.startListening()
        categoryRepository.startListening()
    }

    suspend fun saveSchedule(docId: String?, data: HashMap<String, Any?>) {
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

