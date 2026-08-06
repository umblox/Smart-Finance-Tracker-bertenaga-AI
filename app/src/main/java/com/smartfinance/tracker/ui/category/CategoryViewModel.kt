package com.smartfinance.tracker.ui.category

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartfinance.tracker.R
import com.smartfinance.tracker.data.model.Category
import com.smartfinance.tracker.data.repository.CategoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CategoryUiState(
    val currentFilter: String = "EXPENSE",
    val parentCategories: List<Category> = emptyList(),
    val subCategories: List<Category> = emptyList(),
    val allCategoriesForEditor: List<Category> = emptyList()
)

// 🔥 FIX: Upgrade ke AndroidViewModel agar aman mengakses String Resources
class CategoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CategoryRepository()

    private val _uiState = MutableStateFlow(CategoryUiState())
    val uiState: StateFlow<CategoryUiState> = _uiState

    init {
        repository.startListening()
        viewModelScope.launch {
            repository.categories.collect { cats ->
                processCategories(cats, _uiState.value.currentFilter)
            }
        }
    }

    fun setFilter(filter: String) {
        processCategories(repository.categories.value, filter)
    }

    private fun processCategories(allCats: List<Category>, filter: String) {
        val filtered = allCats.filter { it.type == filter }
        val parents = filtered.filter { it.parentCategoryId == null }.sortedBy { it.name }
        val subs = filtered.filter { it.parentCategoryId != null }

        _uiState.value = CategoryUiState(
            currentFilter = filter,
            parentCategories = parents,
            subCategories = subs,
            allCategoriesForEditor = allCats
        )
    }

    suspend fun validateAndSaveCategory(
        docId: String?, currentNumericId: Long?, name: String, type: String, 
        iconName: String, isLocked: Boolean, parentId: Long?
    ) {
        // 🔥 FIX: Menggunakan string dinamis yang mendukung dwibahasa
        if (name.isBlank()) throw Exception(getApplication<Application>().getString(R.string.category_err_empty_name))
        
        val targetDocId = if (docId.isNullOrEmpty()) "cat_${System.currentTimeMillis()}" else docId
        val targetNumericId = currentNumericId ?: System.currentTimeMillis()

        val data = HashMap<String, Any>().apply {
            put("id", targetNumericId)
            put("name", name.trim())
            put("type", type)
            put("iconName", iconName)
            put("isLocked", isLocked)
            if (parentId != null) put("parentCategoryId", parentId)
        }
        
        repository.saveCategory(targetDocId, data)
    }

    suspend fun deleteCategoryFromCloud(docId: String) {
        repository.deleteCategory(docId)
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopListening()
    }
}
