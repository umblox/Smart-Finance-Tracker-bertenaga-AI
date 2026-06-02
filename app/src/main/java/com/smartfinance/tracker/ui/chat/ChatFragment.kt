package com.smartfinance.tracker.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.smartfinance.tracker.ai.FinancialAssistant
import com.smartfinance.tracker.ai.GeminiClient
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.repository.FinanceRepository // Import repositori milikmu
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

        // 1. Inisialisasi sesuai dengan arsitektur FinanceRepository milikmu
        val db = AppDatabase.getDatabase(requireContext())
        val repository = FinanceRepository(db.transactionDao(), db.categoryDao(), db.debtDao())
        val assistant = FinancialAssistant(repository)
        geminiClient = GeminiClient(requireContext(), assistant)

        // 2. Menggunakan akses binding yang aman untuk layout chat kamu
        binding.btnSend.setOnClickListener {
            val message = binding.etMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                sendChatToAI(message)
            }
        }
    }

    private fun sendChatToAI(message: String) {
        binding.tvChatHistory.append("\nAnda: $message\n")
        binding.etMessage.setText("")
        
        // Mengamankan properti perubahan status view di Android
        binding.btnSend.isEnabled = false 

        lifecycleScope.launch {
            binding.tvChatHistory.append("AI sedang berpikir...\n")
            
            val response = geminiClient.sendMessageToAI(message)
            
            val currentText = binding.tvChatHistory.text.toString()
            val cleanedText = currentText.replace("AI sedang berpikir...\n", "")
            binding.tvChatHistory.text = cleanedText
            
            binding.tvChatHistory.append("AI: $response\n\n")
            binding.btnSend.isEnabled = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
