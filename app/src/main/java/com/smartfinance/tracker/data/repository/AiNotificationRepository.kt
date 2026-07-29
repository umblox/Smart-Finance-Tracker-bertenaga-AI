package com.smartfinance.tracker.data.repository

import com.smartfinance.tracker.data.local.DatabaseProvider
import com.smartfinance.tracker.data.model.AiNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AiNotificationRepository {
    private val dao = DatabaseProvider.db.aiNotificationDao()
    private var listenJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _notifications = MutableStateFlow<List<AiNotification>>(emptyList())
    val notifications: StateFlow<List<AiNotification>> = _notifications

    fun startListening() {
        if (listenJob != null) return
        listenJob = scope.launch {
            dao.getAll().collect { list ->
                _notifications.value = list
            }
        }
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
    }

    suspend fun saveNotification(notif: AiNotification) {
        val docId = notif.id.ifEmpty { java.util.UUID.randomUUID().toString() }
        val finalNotif = notif.copy(id = docId)
        dao.insert(finalNotif)
    }

    suspend fun markAllAsRead() {
        dao.markAllAsRead()
    }

    suspend fun deleteNotification(id: String) {
        dao.delete(id)
    }

    suspend fun cleanupOldNotifications() {
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        dao.deleteOldNotifications(sevenDaysAgo)
    }
}
