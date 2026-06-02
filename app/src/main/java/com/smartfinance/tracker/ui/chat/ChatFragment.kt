package com.smartfinance.tracker.ui.chat

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.smartfinance.tracker.ai.FinancialAssistant
import com.smartfinance.tracker.ai.GeminiClient
import com.smartfinance.tracker.databinding.FragmentChatBinding
import kotlinx.coroutines.launch

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var geminiClient: GeminiClient

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val assistant = FinancialAssistant(requireContext())
        geminiClient = GeminiClient(requireContext(), assistant)

        // MUAT RIWAYAT CHAT LAMA DARI PENYIMPANAN PERMANEN HP
        val prefs = requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        val savedChat = prefs.getString("chat_history_backup", "")
        if (!savedChat.isNullContentOrEmpty()) {
            binding.tvChatHistory.text = savedChat
        }

        binding.btnSend.setOnClickListener {
            val message = binding.etMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                sendChatToAI(message)
            }
        }
    }

    private fun sendChatToAI(message: String) {
        val prefs = requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        
        binding.tvChatHistory.append("\nAnda: $message\n")
        binding.etMessage.setText("")
        binding.btnSend.isEnabled = false 

        lifecycleScope.launch {
            binding.tvChatHistory.append("AI sedang berpikir...\n")
            
            val response = geminiClient.sendMessageToAI(message)
            
            val currentText = binding.tvChatHistory.text.toString()
            val cleanedText = currentText.replace("AI sedang berpikir...\n", "")
            
            val updatedChat = "$cleanedText\nAI: $response\n\n"
            binding.tvChatHistory.text = updatedChat
            binding.btnSend.isEnabled = true
            
            // SIMPAN PERMANEN DETIK INI JUGA
            prefs.edit().putString("chat_history_backup", updatedChat).apply()
        }
    }

    private fun String?.isNullContentOrEmpty(): Boolean = this == null || this.trim().isEmpty()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
