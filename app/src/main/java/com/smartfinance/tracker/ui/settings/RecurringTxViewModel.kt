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

    // 🔥 LOGIKA BISNIS PINDAH KE SINI: Validasi dan Pembuatan Data
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
        if (category == null) throw Exception("Harap pilih Kategori terlebih dahulu!")
        if (note.isBlank() || amountStr.isBlank()) throw Exception("Harap isi Catatan dan Nominal!")
        
        val amount = amountStr.toDoubleOrNull() ?: 0.0
        if (amount <= 0) throw Exception("Nominal tidak valid!")

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
