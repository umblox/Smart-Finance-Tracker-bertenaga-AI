package com.smartfinance.tracker.data.repository

import com.google.firebase.firestore.ListenerRegistration
import com.smartfinance.tracker.data.model.Category
import com.smartfinance.tracker.utils.FirebaseManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

class CategoryRepository {
    private val firestore = FirebaseManager.getFirestore()
    private var listener: ListenerRegistration? = null

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories

    fun startListening() {
        if (listener != null) return
        listener = firestore.collection("categories")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener

                val list = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    Category(
                        docId = doc.id,
                        id = doc.getLong("id") ?: 0L,
                        name = data["name"] as? String ?: "",
                        type = data["type"] as? String ?: "EXPENSE",
                        iconName = data["iconName"] as? String ?: "ic_custom",
                        parentCategoryId = (data["parentCategoryId"] as? Number)?.toLong(),
                        isLocked = data["isLocked"] as? Boolean ?: false
                    )
                }
                _categories.value = list
            }
    }

    fun stopListening() {
        listener?.remove()
        listener = null
    }

    // FIX: Menggunakan HashMap<String, Any> secara tegas
    suspend fun saveCategory(docId: String, categoryMap: HashMap<String, Any>) {
        firestore.collection("categories").document(docId).set(categoryMap).await()
    }

    suspend fun deleteCategory(docId: String) {
        firestore.collection("categories").document(docId).delete().await()
    }
}
