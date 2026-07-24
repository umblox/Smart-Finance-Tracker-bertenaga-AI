package com.smartfinance.tracker.data.repository

import android.content.Context
import com.smartfinance.tracker.data.model.ChatMessage
import com.smartfinance.tracker.utils.FirebaseManager
import kotlinx.coroutines.tasks.await

class ChatRepository(private val context: Context) {
    private val firestore = FirebaseManager.getFirestore()
    private val prefs = context.getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)

    suspend fun getChatHistory(): List<ChatMessage> {
        val savedChat = prefs.getString("chat_history_backup_v4", "")
        val localList = mutableListOf<ChatMessage>()
        
        // Membaca dari SharedPreferences lokal terlebih dahulu (Fast Load)
        if (!savedChat.isNullOrEmpty()) {
            savedChat.split("\n").forEach { line ->
                if (line.trim().isNotEmpty()) {
                    if (line.startsWith("[USER]")) localList.add(ChatMessage(line.substring(6).replace("<br>", "\n"), true))
                    if (line.startsWith("[AI]")) localList.add(ChatMessage(line.substring(4).replace("<br>", "\n"), false))
                }
            }
            return localList
        }

        // Jika tidak ada di lokal, tarik dari Cloud Firestore
        try {
            val document = firestore.collection("user_chat").document("main_chat_history").get().await()
            if (document != null && document.exists()) {
                val history = document.get("history") as? List<Map<String, Any>>
                history?.forEach { msgMap ->
                    val text = msgMap["text"] as? String ?: ""
                    val isUser = msgMap["isUser"] as? Boolean ?: false
                    if (text.isNotEmpty()) localList.add(ChatMessage(text, isUser))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return localList
    }

    suspend fun saveChatHistory(messageList: List<ChatMessage>) {
        // 1. Simpan token format ke lokal
        val backupBuilder = java.lang.StringBuilder()
        messageList.forEach { 
            val prefix = if (it.isUser) "[USER]" else "[AI]"
            val safeText = it.text.replace("\n", "<br>")
            backupBuilder.append("$prefix$safeText\n")
        }
        prefs.edit().putString("chat_history_backup_v4", backupBuilder.toString()).apply()
        
        // 2. Simpan murni ke Cloud Firestore
        val chatList = messageList.map { msg ->
            hashMapOf("text" to msg.text, "isUser" to msg.isUser, "timestamp" to System.currentTimeMillis())
        }
        firestore.collection("user_chat").document("main_chat_history")
            .set(hashMapOf("updatedAt" to System.currentTimeMillis(), "history" to chatList))
    }

    suspend fun clearHistory() {
        prefs.edit().remove("chat_history_backup_v4").apply()
        firestore.collection("user_chat").document("main_chat_history").delete().await()
    }
}
