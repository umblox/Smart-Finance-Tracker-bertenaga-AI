package com.smartfinance.tracker.ui.chat

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.smartfinance.tracker.ai.FinancialAssistant
import com.smartfinance.tracker.ai.GroqClient // Menggunakan GroqClient yang baru
import com.smartfinance.tracker.data.model.ChatMessage
import com.smartfinance.tracker.databinding.FragmentChatBinding
import kotlinx.coroutines.launch

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var groqClient: GroqClient // Menggunakan GroqClient
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
        groqClient = GroqClient(requireContext(), assistant) // Menggunakan GroqClient

        chatAdapter = ChatAdapter(messageList)
        binding.rvChatHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvChatHistory.adapter = chatAdapter

        binding.btnSend.apply {
            visibility = View.VISIBLE 
            text = "" 
            icon = null 
            setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_send, 0, 0, 0)
            
            val density = requireContext().resources.displayMetrics.density
            val dynamicPaddingLeft = (14 * density).toInt() 
            setPadding(dynamicPaddingLeft, 0, 0, 0)
            
            stateListAnimator = null 
            elevation = 14 * density 
            cornerRadius = (27 * density).toInt() 
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#008080"))
        }

        val prefs = requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        val savedChat = prefs.getString("chat_history_backup_v4", "")
        
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
                    prefs.edit().remove("chat_history_backup_v4").apply()
                    messageList.clear()
                    messageList.add(ChatMessage("Riwayat chat telah dibersihkan.\n\nAda yang bisa saya bantu hari ini?", false))
                    chatAdapter.notifyDataSetChanged()
                }
                setNegativeButton("Batal", null)
                show()
            }
        }
    }

    private fun sendChatToAI(message: String) {
        val prefs = requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        
        messageList.add(ChatMessage(message, true))
        chatAdapter.notifyItemInserted(messageList.size - 1)
        binding.rvChatHistory.scrollToPosition(messageList.size - 1)
        
        binding.etMessage.setText("")
        
        binding.btnSend.isEnabled = false 
        binding.btnSend.alpha = 0.5f

        messageList.add(ChatMessage("AI sedang berpikir...", false))
        chatAdapter.notifyItemInserted(messageList.size - 1)
        binding.rvChatHistory.scrollToPosition(messageList.size - 1)

        lifecycleScope.launch {
            val cleanNarrationResponse = groqClient.sendMessageToAI(message) // Panggil sendMessageToAI dari GroqClient
            
            if (messageList.isNotEmpty()) {
                messageList.removeAt(messageList.size - 1)
            }

            messageList.add(ChatMessage(cleanNarrationResponse.trim(), false))
            chatAdapter.notifyDataSetChanged()
            binding.rvChatHistory.post { binding.rvChatHistory.scrollToPosition(messageList.size - 1) }
            
            binding.btnSend.isEnabled = true
            binding.btnSend.alpha = 1.0f
            
            val backupBuilder = StringBuilder()
            messageList.forEach { 
                val prefix = if (it.isUser) "[USER]" else "[AI]"
                backupBuilder.append("$prefix${it.text}\n")
            }
            prefs.edit().putString("chat_history_backup_v4", backupBuilder.toString()).apply()
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
