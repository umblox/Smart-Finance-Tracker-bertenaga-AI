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
import com.smartfinance.tracker.ai.GroqClient
import com.smartfinance.tracker.data.model.ChatMessage
import com.smartfinance.tracker.databinding.FragmentChatBinding
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var groqClient: GroqClient
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
        groqClient = GroqClient(contextRef, assistant)

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
            // 🔥 FULL CLOUD: Tarik riwayat obrolan asli langsung dari Firestore Cloud server
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
        
        // 🔥 FIX MUTLAK: Masukkan pesan user secara linear tanpa pemotongan kaku logika lokal
        messageList.add(ChatMessage(message, true))
        chatAdapter.notifyItemInserted(messageList.size - 1)
        binding.rvChatHistory.scrollToPosition(messageList.size - 1)
        binding.etMessage.setText("")
        
        // Kunci tombol input selama AI memproses data di latar belakang
        binding.btnSend.isEnabled = false
        binding.btnSend.alpha = 0.5f

        messageList.add(ChatMessage("AI sedang berpikir...", false))
        chatAdapter.notifyItemInserted(messageList.size - 1)

        lifecycleScope.launch {
            // 1. Alirkan pesan langsung menuju server Groq Llama 3.1
            val finalResponseText = groqClient.sendMessageToAI(message)
            
            // Hapus bubble animasi "AI sedang berpikir..."
            if (messageList.isNotEmpty()) { messageList.removeAt(messageList.size - 1) }

            // 2. Tampilkan teks respons analisis finansial riil dari Llama ke layar HP
            messageList.add(ChatMessage(finalResponseText.trim(), false))

            chatAdapter.notifyDataSetChanged()
            binding.rvChatHistory.post { binding.rvChatHistory.scrollToPosition(messageList.size - 1) }
            
            // Buka kembali kunci tombol kirim
            binding.btnSend.isEnabled = true
            binding.btnSend.alpha = 1.0f

            // 3. Cadangkan log obrolan ke SharedPreferences lokal
            val backupBuilder = StringBuilder()
            messageList.forEach { 
                val prefix = if (it.isUser) "[USER]" else "[AI]"
                backupBuilder.append("$prefix${it.text.replace("\n", " ")}\n")
            }
            prefs.edit().putString("chat_history_backup_v4", backupBuilder.toString()).apply()
            
            // 4. Cadangkan log obrolan secara permanen ke database awan user_chat Firestore
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
                if (line.startsWith("[USER]")) messageList.add(ChatMessage(line.substring(6), true))
                if (line.startsWith("[AI]")) messageList.add(ChatMessage(line.substring(4), false))
            }
        }
        chatAdapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
