package com.smartfinance.tracker.data.repository

import android.content.Context
import com.smartfinance.tracker.data.model.ChatMessage

class ChatRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)

    suspend fun getChatHistory(): List<ChatMessage> {
        val savedChat = prefs.getString("chat_history_backup_v4", "")
        val localList = mutableListOf<ChatMessage>()
        
        if (!savedChat.isNullOrEmpty()) {
            savedChat.split("\n").forEach { line ->
                if (line.trim().isNotEmpty()) {
                    if (line.startsWith("[USER]")) localList.add(ChatMessage(line.substring(6).replace("<br>", "\n"), true))
                    if (line.startsWith("[AI]")) localList.add(ChatMessage(line.substring(4).replace("<br>", "\n"), false))
                }
            }
        }
        return localList
    }

    suspend fun saveChatHistory(messageList: List<ChatMessage>) {
        val backupBuilder = java.lang.StringBuilder()
        messageList.forEach { 
            val prefix = if (it.isUser) "[USER]" else "[AI]"
            val safeText = it.text.replace("\n", "<br>")
            backupBuilder.append("$prefix$safeText\n")
        }
        prefs.edit().putString("chat_history_backup_v4", backupBuilder.toString()).apply()
    }

    suspend fun clearHistory() {
        prefs.edit().remove("chat_history_backup_v4").apply()
    }
}
