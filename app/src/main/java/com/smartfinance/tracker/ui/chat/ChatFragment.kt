package com.smartfinance.tracker.ui.chat

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
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

        // Muat riwayat backup chat permanen
        val prefs = requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        val savedChat = prefs.getString("chat_history_backup", "")
        if (!savedChat.isNullOrEmpty()) {
            binding.tvChatHistory.text = savedChat
            autoScrollToBottom()
        }

        binding.btnSend.setOnClickListener {
            val message = binding.etMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                sendChatToAI(message)
            }
        }

        // FITUR TAMBAHAN: Bersihkan total riwayat obrolan dari memori internal HP
        binding.root.findViewById<Button>(com.smartfinance.tracker.R.id.btnClearChat)?.setOnClickListener {
            prefs.edit().remove("chat_history_backup").apply()
            binding.tvChatHistory.text = "Riwayat chat telah dibersihkan.\n\nAda yang bisa saya bantu hari ini?"
        } ?: run {
            // Jika ID btnClearChat belum didefinisikan di XML, pasang interaksi Long-Click pada tvChatHistory sebagai alternatif darurat
            binding.tvChatHistory.setOnLongClickListener {
                prefs.edit().remove("chat_history_backup").apply()
                binding.tvChatHistory.text = "Riwayat chat telah dibersihkan."
                true
            }
        }
    }

    private fun sendChatToAI(message: String) {
        val prefs = requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        
        binding.tvChatHistory.append("\nAnda: $message\n")
        binding.etMessage.setText("")
        binding.btnSend.isEnabled = false 
        autoScrollToBottom()

        lifecycleScope.launch {
            binding.tvChatHistory.append("AI sedang berpikir...\n")
            autoScrollToBottom()
            
            val response = geminiClient.sendMessageToAI(message)
            
            val currentText = binding.tvChatHistory.text.toString()
            val cleanedText = currentText.replace("AI sedang berpikir...\n", "")
            
            val updatedChat = "$cleanedText\nAI: $response\n\n"
            binding.tvChatHistory.text = updatedChat
            binding.btnSend.isEnabled = true
            
            prefs.edit().putString("chat_history_backup", updatedChat).apply()
            autoScrollToBottom()
        }
    }

    private fun autoScrollToBottom() {
        binding.tvChatHistory.post {
            // Cari parent scrollview secara dinamis untuk menjamin kecocokan layout XML
            var parentView = binding.tvChatHistory.parent
            while (parentView != null) {
                if (parentView is ScrollView) {
                    parentView.fullScroll(ScrollView.FOCUS_DOWN)
                    break
                }
                parentView = parentView.parent
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
