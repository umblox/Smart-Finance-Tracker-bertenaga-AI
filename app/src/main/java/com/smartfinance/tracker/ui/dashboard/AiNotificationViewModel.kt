package com.smartfinance.tracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartfinance.tracker.data.model.AiNotification
import com.smartfinance.tracker.data.repository.AiNotificationRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AiNotificationViewModel : ViewModel() {
    private val repository = AiNotificationRepository()
    val notifications: StateFlow<List<AiNotification>> = repository.notifications

    init {
        repository.startListening()
        // 🔥 AUTO-CLEANUP: Setiap kali ViewModel hidup, bersihkan notif lama!
        viewModelScope.launch { repository.cleanupOldNotifications() }
    }

    fun markAllAsRead() {
        viewModelScope.launch { repository.markAllAsRead() }
    }

    fun deleteNotification(id: String) {
        viewModelScope.launch { repository.deleteNotification(id) }
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopListening()
    }
}
