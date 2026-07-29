package com.smartfinance.tracker.data.repository

import com.smartfinance.tracker.data.local.DatabaseProvider
import com.smartfinance.tracker.data.model.Category
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.HashMap

class CategoryRepository {
    private val dao = DatabaseProvider.db.categoryDao()
    private var listenJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories

    fun startListening() {
        if (listenJob != null) return
        listenJob = scope.launch {
            dao.getAll().collect { list ->
                _categories.value = list
            }
        }
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
    }

    suspend fun saveCategory(docId: String, data: HashMap<String, Any>) {
        val cat = Category(
            docId = docId,
            id = (data["id"] as? Number)?.toLong() ?: 0L,
            name = data["name"] as? String ?: "",
            type = data["type"] as? String ?: "EXPENSE",
            iconName = data["iconName"] as? String ?: "ic_custom",
            parentCategoryId = (data["parentCategoryId"] as? Number)?.toLong(),
            isLocked = data["isLocked"] as? Boolean ?: false
        )
        dao.insert(cat)
    }

    suspend fun deleteCategory(docId: String) {
        dao.delete(docId)
    }
}
