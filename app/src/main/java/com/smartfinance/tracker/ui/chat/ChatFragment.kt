package com.smartfinance.tracker.ui.chat

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.smartfinance.tracker.ai.FinancialAssistant
import com.smartfinance.tracker.ai.AIClient
import com.smartfinance.tracker.data.model.ChatMessage
import com.smartfinance.tracker.databinding.FragmentChatBinding
import kotlinx.coroutines.launch
import java.util.Locale

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var aiClient: AIClient
    private lateinit var assistant: FinancialAssistant
    private val messageList = ArrayList<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val contextRef = requireContext()
        assistant = FinancialAssistant(contextRef)
        aiClient = AIClient(contextRef, assistant)

        chatAdapter = ChatAdapter(messageList)
        binding.rvChatHistory.layoutManager = LinearLayoutManager(contextRef)
        binding.rvChatHistory.adapter = chatAdapter

        binding.btnSend.apply {
            visibility = View.VISIBLE
            text = ""
            icon = null
            setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_send, 0, 0, 0)
            val density = contextRef.resources.displayMetrics.density
            setPadding((14 * density).toInt(), 0, 0, 0)
            stateListAnimator = null
            elevation = 14 * density
            cornerRadius = (27 * density).toInt()
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#008080"))
        }

        val prefs = contextRef.getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        val savedChat = prefs.getString("chat_history_backup_v4", "")
        
        if (!savedChat.isNullOrEmpty()) { 
            loadBackupToAdapter(savedChat) 
        } else {
            // 🔥 FULL CLOUD: Tarik riwayat asli dari Firestore Cloud server
            firestore.collection("user_chat")
                .document("main_chat_history")
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val history = document.get("history") as? List<Map<String, Any>>
                        if (!history.isNullOrEmpty()) {
                            messageList.clear()
                            history.forEach { msgMap ->
                                val text = msgMap["text"] as? String ?: ""
                                val isUser = msgMap["isUser"] as? Boolean ?: false
                                if (text.isNotEmpty()) messageList.add(ChatMessage(text, isUser))
                            }
                            chatAdapter.notifyDataSetChanged()
                            binding.rvChatHistory.post { binding.rvChatHistory.scrollToPosition(messageList.size - 1) }
                        }
                    }
                }
        }

        binding.btnSend.setOnClickListener {
            val message = binding.etMessage.text.toString().trim()
            if (message.isNotEmpty()) { sendChatToAI(message) }
        }

        binding.btnClearChat.setOnClickListener {
            AlertDialog.Builder(contextRef).apply {
                setTitle("🗑️ Bersihkan Riwayat Chat?")
                setMessage("Apakah Anda yakin ingin menghapus seluruh riwayat percakapan secara permanen dari Cloud?")
                setPositiveButton("Ya, Hapus Semua") { _, _ ->
                    prefs.edit().remove("chat_history_backup_v4").apply()
                    firestore.collection("user_chat").document("main_chat_history").delete()
                    messageList.clear()
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

        lifecycleScope.launch {
            val finalResponseText = aiClient.sendMessageToAI(message)
            
            if (messageList.isNotEmpty()) { messageList.removeAt(messageList.size - 1) }

            messageList.add(ChatMessage(finalResponseText.trim(), false))

            chatAdapter.notifyDataSetChanged()
            binding.rvChatHistory.post { binding.rvChatHistory.scrollToPosition(messageList.size - 1) }
            
            binding.btnSend.isEnabled = true
            binding.btnSend.alpha = 1.0f

            // 🔥 FIX AKURAT 1: Ganti enter ke token khusus '<br>' agar layout tidak patah saat disimpan
            val backupBuilder = StringBuilder()
            messageList.forEach { 
                val prefix = if (it.isUser) "[USER]" else "[AI]"
                val safeText = it.text.replace("\n", "<br>")
                backupBuilder.append("$prefix$safeText\n")
            }
            prefs.edit().putString("chat_history_backup_v4", backupBuilder.toString()).apply()
            
            // Simpan data mentah asli ke cloud tanpa modifikasi token karena cloud mendukung multiline
            val chatList = ArrayList<HashMap<String, Any>>()
            messageList.forEach { msg ->
                chatList.add(hashMapOf("text" to msg.text, "isUser" to msg.isUser, "timestamp" to System.currentTimeMillis()))
            }
            firestore.collection("user_chat").document("main_chat_history")
                .set(hashMapOf("updatedAt" to System.currentTimeMillis(), "history" to chatList))
        }
    }

    private fun loadBackupToAdapter(backupStr: String) {
        messageList.clear()
        backupStr.split("\n").forEach { line ->
            if (line.trim().isNotEmpty()) {
                // 🔥 FIX AKURAT 2: Kembalikan token '<br>' menjadi enter asli '\n' saat dimuat ulang ke adapter
                if (line.startsWith("[USER]")) {
                    val userText = line.substring(6).replace("<br>", "\n")
                    messageList.add(ChatMessage(userText, true))
                }
                if (line.startsWith("[AI]")) {
                    val aiText = line.substring(4).replace("<br>", "\n")
                    messageList.add(ChatMessage(aiText, false))
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
