package com.smartfinance.tracker.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

class CategoryViewModel : ViewModel() {
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

    // FIX: Menggunakan HashMap<String, Any> secara tegas
    suspend fun saveCategoryToCloud(docId: String, data: HashMap<String, Any>) {
        repository.saveCategory(docId, data)
    }

    suspend fun deleteCategoryFromCloud(docId: String) {
        repository.deleteCategory(docId)
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopListening()
    }
}
