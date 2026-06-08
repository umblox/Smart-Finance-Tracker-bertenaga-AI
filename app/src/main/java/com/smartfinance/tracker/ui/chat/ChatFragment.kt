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
import com.smartfinance.tracker.ai.FinancialAssistant
import com.smartfinance.tracker.ai.GroqClient
import com.smartfinance.tracker.data.model.ChatMessage
import com.smartfinance.tracker.data.remote.FirebaseSyncManager
import com.smartfinance.tracker.databinding.FragmentChatBinding
import kotlinx.coroutines.launch

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var groqClient: GroqClient
    private val messageList = ArrayList<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var syncManager: FirebaseSyncManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val contextRef = requireContext()
        val assistant = FinancialAssistant(contextRef)
        groqClient = GroqClient(contextRef, assistant)
        syncManager = FirebaseSyncManager(contextRef)

        chatAdapter = ChatAdapter(messageList)
        binding.rvChatHistory.layoutManager = LinearLayoutManager(contextRef)
        binding.rvChatHistory.adapter = chatAdapter

        binding.btnSend.apply {
            visibility = View.VISIBLE 
            text = "" 
            icon = null 
            setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_send, 0, 0, 0)
            
            val density = contextRef.resources.displayMetrics.density
            val dynamicPaddingLeft = (14 * density).toInt() 
            setPadding(dynamicPaddingLeft, 0, 0, 0)
            
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
            // Jika preferensi lokal kosong, coba cek apakah ada data chat lama di Firebase Cloud
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("user_chat")
                .document("main_chat_history")
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot != null && snapshot.exists()) {
                        val historyArray = snapshot.get("history") as? List<Map<String, Any>>
                        if (!historyArray.isNullOrEmpty()) {
                            messageList.clear()
                            val backupBuilder = StringBuilder()
                            historyArray.forEach { item ->
                                val text = item["text"] as? String ?: ""
                                val isUser = item["isUser"] as? Boolean ?: false
                                messageList.add(ChatMessage(text, isUser))
                                
                                val prefix = if (isUser) "[USER]" else "[AI]"
                                val cleanLineText = text.replace("\n", " ")
                                backupBuilder.append("$prefix$cleanLineText\n")
                            }
                            prefs.edit().putString("chat_history_backup_v4", backupBuilder.toString()).apply()
                            chatAdapter.notifyDataSetChanged()
                            binding.rvChatHistory.post { binding.rvChatHistory.scrollToPosition(messageList.size - 1) }
                            return@addOnSuccessListener
                        }
                    }
                    
                    // Default fallback jika cloud juga kosong
                    messageList.add(ChatMessage("Halo Umam! Saya asisten keuangan AI kamu. Silakan ketik transaksi seperti 'beli rokok 20000 dan bensin 15000'.", false))
                    chatAdapter.notifyDataSetChanged()
                }
                .addOnFailureListener {
                    messageList.add(ChatMessage("Halo Umam! Saya asisten keuangan AI kamu. Silakan ketik transaksi seperti 'beli rokok 20000 dan bensin 15000'.", false))
                    chatAdapter.notifyDataSetChanged()
                }
        }

        binding.btnSend.setOnClickListener {
            val message = binding.etMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                sendChatToAI(message)
            }
        }

        binding.btnClearChat.setOnClickListener {
            AlertDialog.Builder(contextRef).apply {
                setTitle("🗑️ Bersihkan Riwayat Chat?")
                setMessage("Apakah Anda yakin ingin menghapus seluruh riwayat percakapan secara permanen di HP dan Cloud?")
                setPositiveButton("Ya, Hapus Semua") { _, _ ->
                    // 1. Bersihkan Preferensi Lokal HP
                    prefs.edit().remove("chat_history_backup_v4").apply()
                    
                    // 2. Bersihkan Data Permanen di Firebase Server
                    syncManager.clearChatHistoryFromCloud {
                        activity?.runOnUiThread {
                            Toast.makeText(contextRef, "☁️ Cloud Backup Chat Berhasil Dihapus!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    
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
        val contextRef = requireContext()
        val prefs = contextRef.getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        
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
            val cleanNarrationResponse = groqClient.sendMessageToAI(message)
            
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
                val cleanLineText = it.text.replace("\n", " ")
                backupBuilder.append("$prefix$cleanLineText\n")
            }
            prefs.edit().putString("chat_history_backup_v4", backupBuilder.toString()).apply()
            
            // 🔥 REPLIKASI AWAN CHAT: Kirim salinan riwayat percakapan utuh yang baru ke Firestore
            syncManager.syncChatHistoryToCloud(messageList)
        }
    }

    private fun loadBackupToAdapter(backupStr: String) {
        messageList.clear()
        val lines = backupStr.split("\n")
        lines.forEach { line ->
            if (line.trim().isNotEmpty()) {
                when {
                    line.startsWith("[USER]") -> {
                        messageList.add(ChatMessage(line.substring(6), true))
                    }
                    line.startsWith("[AI]") -> {
                        messageList.add(ChatMessage(line.substring(4), false))
                    }
                }
            }
        }
        if (messageList.isEmpty()) {
            messageList.add(ChatMessage("Halo Umam! Ada yang bisa saya bantu hari ini?", false))
        }
        chatAdapter.notifyDataSetChanged()
        binding.rvChatHistory.post { binding.rvChatHistory.scrollToPosition(messageList.size - 1) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
