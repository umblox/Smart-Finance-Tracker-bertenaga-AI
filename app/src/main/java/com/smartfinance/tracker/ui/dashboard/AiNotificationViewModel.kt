package com.smartfinance.tracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfinance.tracker.data.model.AiNotification
import com.smartfinance.tracker.data.repository.AiNotificationRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AiNotificationViewModel : ViewModel() {
    private val repository = AiNotificationRepository()

    // Mengekspos aliran data notifikasi ke UI
    val notifications: StateFlow<List<AiNotification>> = repository.notifications

    init {
        // Mulai memantau Firestore saat ViewModel dibuat
        repository.startListening()
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            repository.markAllAsRead()
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Hentikan pantauan saat UI ditutup agar hemat memori
        repository.stopListening()
    }
}
