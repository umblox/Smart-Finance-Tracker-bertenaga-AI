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

    // 1. Pantau pesan AI baru secara Real-time
    fun startListening() {
        if (listener != null) return
        listener = firestore.collection("ai_notifications")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50) // Batasi 50 pesan terakhir agar memori ringan
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

    // 2. Simpan Nasihat AI Baru (Akan dipanggil oleh Background Worker nanti)
    suspend fun saveNotification(notif: AiNotification) {
        val notifMap = hashMapOf(
            "title" to notif.title,
            "message" to notif.message,
            "timestamp" to notif.timestamp,
            "type" to notif.type,
            "isRead" to notif.isRead
        )
        // Gunakan UUID acak jika ID kosong
        val docId = if (notif.id.isNotEmpty()) notif.id else java.util.UUID.randomUUID().toString()
        firestore.collection("ai_notifications").document(docId).set(notifMap).await()
    }

    // 3. Tandai semua sudah dibaca (Akan dipanggil saat tombol di UI diklik)
    suspend fun markAllAsRead() {
        val unreadDocs = firestore.collection("ai_notifications")
            .whereEqualTo("isRead", false)
            .get()
            .await()

        // Batch update untuk menghemat kuota write Firestore
        val batch = firestore.batch()
        for (doc in unreadDocs) {
            batch.update(doc.reference, "isRead", true)
        }
        batch.commit().await()
    }
}
