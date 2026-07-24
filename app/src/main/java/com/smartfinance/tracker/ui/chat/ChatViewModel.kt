package com.smartfinance.tracker.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartfinance.tracker.ai.AIClient
import com.smartfinance.tracker.ai.FinancialAssistant
import com.smartfinance.tracker.data.model.ChatMessage
import com.smartfinance.tracker.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isTyping: Boolean = false
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChatRepository(application)
    private val assistant = FinancialAssistant(application)
    private val aiClient = AIClient(application, assistant)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            val history = repository.getChatHistory()
            _uiState.value = ChatUiState(messages = history, isTyping = false)
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        // 1. Tampilkan pesan user
        val currentMessages = _uiState.value.messages.toMutableList()
        currentMessages.add(ChatMessage(text, true))
        
        // 2. Tambahkan indikator AI sedang mengetik
        val typingMessages = currentMessages.toMutableList()
        typingMessages.add(ChatMessage("AI sedang berpikir...", false))
        
        _uiState.value = ChatUiState(messages = typingMessages, isTyping = true)

        viewModelScope.launch {
            // 3. Kirim ke mesin AI (memakan waktu)
            val response = aiClient.sendMessageToAI(text)
            
            // 4. Hapus indikator "sedang berpikir" dan ganti dengan balasan asli
            val finalMessages = currentMessages.toMutableList()
            finalMessages.add(ChatMessage(response.trim(), false))
            
            _uiState.value = ChatUiState(messages = finalMessages, isTyping = false)
            
            // 5. Simpan ke Cloud & Backup secara paralel
            repository.saveChatHistory(finalMessages)
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clearHistory()
            _uiState.value = ChatUiState(messages = emptyList(), isTyping = false)
        }
    }
}
