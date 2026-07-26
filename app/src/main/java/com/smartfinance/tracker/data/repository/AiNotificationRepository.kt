package com.smartfinance.tracker.data.repository

import com.google.firebase.firestore.ListenerRegistration
import com.smartfinance.tracker.data.model.AiNotification
import com.smartfinance.tracker.utils.FirebaseManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

class AiNotificationRepository {
    private val firestore = FirebaseManager.getFirestore()
    private var listener: ListenerRegistration? = null

    private val _notifications = MutableStateFlow<List<AiNotification>>(emptyList())
    val notifications: StateFlow<List<AiNotification>> = _notifications

    fun startListening() {
        if (listener != null) return
        listener = firestore.collection("ai_notifications")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50) 
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener

                val list = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    AiNotification(
                        id = doc.id,
                        title = data["title"] as? String ?: "",
                        message = data["message"] as? String ?: "",
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        type = data["type"] as? String ?: "INFO",
                        isRead = data["isRead"] as? Boolean ?: false
                    )
                }
                _notifications.value = list
            }
    }

    fun stopListening() {
        listener?.remove()
        listener = null
    }

    suspend fun saveNotification(notif: AiNotification) {
        val notifMap = hashMapOf(
            "title" to notif.title,
            "message" to notif.message,
            "timestamp" to notif.timestamp,
            "type" to notif.type,
            "isRead" to notif.isRead
        )
        val docId = notif.id.ifEmpty { java.util.UUID.randomUUID().toString() }
        firestore.collection("ai_notifications").document(docId).set(notifMap).await()
    }

    suspend fun markAllAsRead() {
        val unreadDocs = firestore.collection("ai_notifications")
            .whereEqualTo("isRead", false)
            .get()
            .await()

        val batch = firestore.batch()
        for (doc in unreadDocs) {
            batch.update(doc.reference, "isRead", true)
        }
        batch.commit().await()
    }

    // 🔥 FITUR BARU: Hapus Satu Notifikasi
    suspend fun deleteNotification(id: String) {
        firestore.collection("ai_notifications").document(id).delete().await()
    }

    // 🔥 FITUR BARU: Pembersihan Otomatis (Sampah umur > 7 Hari)
    suspend fun cleanupOldNotifications() {
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        val oldDocs = firestore.collection("ai_notifications")
            .whereLessThan("timestamp", sevenDaysAgo)
            .get()
            .await()

        if (oldDocs.isEmpty) return
        val batch = firestore.batch()
        for (doc in oldDocs) {
            batch.delete(doc.reference)
        }
        batch.commit().await()
    }
}
