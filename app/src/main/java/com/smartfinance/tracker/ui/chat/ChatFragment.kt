package com.smartfinance.tracker.ui.chat

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.smartfinance.tracker.ai.FinancialAssistant
import com.smartfinance.tracker.ai.GeminiClient
import com.smartfinance.tracker.data.model.ChatMessage
import com.smartfinance.tracker.databinding.FragmentChatBinding
import kotlinx.coroutines.launch

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var geminiClient: GeminiClient
    private val messageList = ArrayList<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter

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

        // Setup RecyclerView dengan susunan Vertikal dari atas ke bawah
        chatAdapter = ChatAdapter(messageList)
        binding.rvChatHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvChatHistory.adapter = chatAdapter

        val prefs = requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        val savedChat = prefs.getString("chat_history_backup_v2", "")
        
        if (!savedChat.isNullOrEmpty()) {
            loadBackupToAdapter(savedChat)
        } else {
            messageList.add(ChatMessage("Halo Umam! Saya asisten keuangan AI kamu. Silakan ketik transaksi seperti 'beli rokok 20000' atau 'gajian 2300000'.", false))
            chatAdapter.notifyDataSetChanged()
        }

        binding.btnSend.setOnClickListener {
            val message = binding.etMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                sendChatToAI(message)
            }
        }

        binding.btnClearChat.setOnClickListener {
            AlertDialog.Builder(requireContext()).apply {
                setTitle("🗑️ Bersihkan Riwayat Chat?")
                setMessage("Apakah Anda yakin ingin menghapus seluruh riwayat percakapan?")
                setPositiveButton("Ya, Hapus") { _, _ ->
                    prefs.edit().remove("chat_history_backup_v2").apply()
                    messageList.clear()
                    messageList.add(ChatMessage("Riwayat chat telah dibersihkan.\n\nAda yang bisa saya bantu hari ini?", false))
                    chatAdapter.notifyDataSetChanged()
                    Toast.makeText(requireContext(), "Riwayat dikosongkan!", Toast.LENGTH_SHORT).show()
                }
                setNegativeButton("Batal", null)
                show()
            }
        }
    }

    private fun sendChatToAI(message: String) {
        val prefs = requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        
        // Tambahkan chat kamu ke SISI KANAN
        messageList.add(ChatMessage(message, true))
        chatAdapter.notifyItemInserted(messageList.size - 1)
        binding.rvChatHistory.scrollToPosition(messageList.size - 1)
        
        binding.etMessage.setText("")
        binding.btnSend.isEnabled = false 

        // Tampilkan indikator loading chat
        messageList.add(ChatMessage("AI sedang berpikir...", false))
        chatAdapter.notifyItemInserted(messageList.size - 1)
        binding.rvChatHistory.scrollToPosition(messageList.size - 1)

        lifecycleScope.launch {
            val response = geminiClient.sendMessageToAI(message)
            
            // Hapus indikator loading
            if (messageList.isNotEmpty()) {
                messageList.removeAt(messageList.size - 1)
            }
            
            // Tambahkan balasan Groq ke SISI KIRI
            messageList.add(ChatMessage(response, false))
            chatAdapter.notifyDataSetChanged()
            binding.rvChatHistory.scrollToPosition(messageList.size - 1)
            binding.btnSend.isEnabled = true
            
            // Simpan backup terstruktur string gabungan dengan token pemisah khusus [USER] dan [AI]
            val backupBuilder = StringBuilder()
            messageList.forEach { 
                val prefix = if (it.isUser) "[USER]" else "[AI]"
                backupBuilder.append("$prefix${it.text}\n")
            }
            prefs.edit().putString("chat_history_backup_v2", backupBuilder.toString()).apply()
        }
    }

    private fun loadBackupToAdapter(backupStr: String) {
        messageList.clear()
        val lines = backupStr.split("\n")
        lines.forEach { line ->
            when {
                line.startsWith("[USER]") -> {
                    messageList.add(ChatMessage(line.replace("[USER]", ""), true))
                }
                line.startsWith("[AI]") -> {
                    messageList.add(ChatMessage(line.replace("[AI]", ""), false))
                }
            }
        }
        chatAdapter.notifyDataSetChanged()
        binding.rvChatHistory.post { binding.rvChatHistory.scrollToPosition(messageList.size - 1) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
