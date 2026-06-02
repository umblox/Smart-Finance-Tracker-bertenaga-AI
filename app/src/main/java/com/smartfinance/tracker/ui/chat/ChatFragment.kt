package com.smartfinance.tracker.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.smartfinance.tracker.ai.FinancialAssistant
import com.smartfinance.tracker.ai.GeminiClient
import com.smartfinance.tracker.data.local.AppDatabase
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

        // Inisialisasi asisten finansial hybrid & database lokal
        val db = AppDatabase.getDatabase(requireContext())
        val assistant = FinancialAssistant(db.transactionDao(), db.categoryDao(), db.debtDao())
        geminiClient = GeminiClient(requireContext(), assistant)

        // AKTIVASI TOMBOL KIRIM
        binding.btnSend.setOnClickListener {
            val message = binding.etMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                sendChatToAI(message)
            }
        }
    }

    private fun sendChatToAI(message: String) {
        // Tampilkan teks inputanmu di area layar chat (simulasi respons cepat)
        binding.tvChatHistory.append("\nAnda: $message\n")
        binding.etMessage.setText("")
        binding.btnSend.isEnabled = false // Kunci tombol saat AI berpikir

        lifecycleScope.launch {
            binding.tvChatHistory.append("AI sedang berpikir...\n")
            
            // Panggil mesin Gemini
            val response = geminiClient.sendMessageToAI(message)
            
            // Hapus teks loading dan cetak jawaban final Gemini
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
