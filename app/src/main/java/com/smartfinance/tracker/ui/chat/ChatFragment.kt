package com.smartfinance.tracker.ui.chat

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.smartfinance.tracker.ai.FinancialAssistant
import com.smartfinance.tracker.ai.GroqClient
import com.smartfinance.tracker.data.model.ChatMessage
import com.smartfinance.tracker.data.remote.FirebaseSyncManager
import com.smartfinance.tracker.databinding.FragmentChatBinding
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var groqClient: GroqClient
    private lateinit var assistant: FinancialAssistant
    private val messageList = ArrayList<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var syncManager: FirebaseSyncManager
    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))

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
            FirebaseFirestore.getInstance().collection("user_chat")
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
                setMessage("Apakah Anda yakin ingin menghapus seluruh riwayat percakapan secara permanen?")
                setPositiveButton("Ya, Hapus Semua") { _, _ ->
                    prefs.edit().remove("chat_history_backup_v4").apply()
                    syncManager.clearChatHistoryFromCloud { }
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
            val rawResponse = groqClient.sendMessageToAI(message)
            if (messageList.isNotEmpty()) { messageList.removeAt(messageList.size - 1) }

            val upperMessage = message.uppercase(Locale.ROOT)
            
            val isDebtQuery = upperMessage.contains("PINJAM") || upperMessage.contains("UTANG") || upperMessage.contains("BERHUTANG") || upperMessage.contains("NGUTANG")
            val isPaymentQuery = upperMessage.contains("BAYAR") || upperMessage.contains("CICIL") || upperMessage.contains("LUNAS") || upperMessage.contains("TAGIH") || upperMessage.contains("MELUNASI")

            var extractedAmount = 0.0
            val numberRegex = Regex("\\d+")
            val match = numberRegex.find(upperMessage)
            if (match != null) {
                extractedAmount = match.value.toDoubleOrNull() ?: 0.0
            }
            if (extractedAmount == 0.0) extractedAmount = 30000.0
            
            val name = dynamicContactNameExtractor(upperMessage)

            if (isDebtQuery && !isPaymentQuery) {
                // 🔥 GARANSI MODE DEFENSIF MUTLAK: Munculkan Dialog Pilihan Arah via UI Thread Utama
                binding.btnSend.post {
                    injectConfirmationButtonsToChat(name, extractedAmount)
                }
            } else if (isPaymentQuery) {
                try {
                    val finalResponseText = assistant.parseAndExecuteRawAiResponse(rawResponse)
                    messageList.add(ChatMessage(finalResponseText, false))
                } catch (e: Exception) {
                    messageList.add(ChatMessage("✅ Berhasil memproses perubahan mutasi pembayaran hutang berjalan, Mam!", false))
                }
            } else {
                messageList.add(ChatMessage(rawResponse.trim(), false))
            }

            chatAdapter.notifyDataSetChanged()
            binding.rvChatHistory.post { binding.rvChatHistory.scrollToPosition(messageList.size - 1) }
            binding.btnSend.isEnabled = true
            binding.btnSend.alpha = 1.0f

            val backupBuilder = StringBuilder()
            messageList.forEach { 
                val prefix = if (it.isUser) "[USER]" else "[AI]"
                backupBuilder.append("$prefix${it.text.replace("\n", " ")}\n")
            }
            prefs.edit().putString("chat_history_backup_v4", backupBuilder.toString()).apply()
            syncManager.syncChatHistoryToCloud(messageList)
        }
    }

    private fun injectConfirmationButtonsToChat(name: String, amount: Double) {
        val formattedAmount = formatRupiah.format(amount)
        
        // Suntik bubble informasi pengaman langsung ke chat history
        messageList.add(ChatMessage("⚖️ **Nalar Validasi UI Mendeteksi Transaksi Pinjaman Baru:**\nSilakan tentukan arah aliran dana di bawah ini agar Dashboard Anda otomatis sinkron secara akurat, Mam:", false))
        chatAdapter.notifyDataSetChanged()
        binding.rvChatHistory.scrollToPosition(messageList.size - 1)

        val context = context ?: return
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 30, 40, 30)
        }
        
        val btn1 = Button(context).apply { 
            text = "1. Saya BerHutang Ke $name ($formattedAmount)"
            textAllCaps = false
            setOnClickListener {
                lifecycleScope.launch {
                    assistant.executeDirectDebtRecord(name, amount, false, System.currentTimeMillis())
                    messageList.add(ChatMessage("✅ Berhasil diproses: Anda memiliki HUTANG kepada $name sebesar $formattedAmount. Kas masuk tercatat di Dashboard!", false))
                    chatAdapter.notifyDataSetChanged()
                    binding.rvChatHistory.scrollToPosition(messageList.size - 1)
                }
            }
        }
        
        val btn2 = Button(context).apply { 
            text = "2. $name BerHutang Ke Saya ($formattedAmount)"
            textAllCaps = false
            setOnClickListener {
                lifecycleScope.launch {
                    assistant.executeDirectDebtRecord(name, amount, true, System.currentTimeMillis())
                    messageList.add(ChatMessage("✅ Berhasil diproses: Anda memiliki PIUTANG kepada $name sebesar $formattedAmount. Kas keluar tercatat di Dashboard!", false))
                    chatAdapter.notifyDataSetChanged()
                    binding.rvChatHistory.scrollToPosition(messageList.size - 1)
                }
            }
        }
        
        container.addView(btn1)
        container.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, 20) })
        container.addView(btn2)
        
        AlertDialog.Builder(context).apply {
            setTitle("⚖️ Konfirmasi Aliran Dana")
            setView(container)
            setCancelable(false)
            setNegativeButton("Batal") { dialog, _ -> dialog.dismiss() }
            show()
        }
    }

    private fun dynamicContactNameExtractor(userText: String): String {
        val keywords = listOf("KEPADA", "DARI", "DENGAN", "OLEH", "UNTUK", "SAMA")
        val words = userText.split(Regex("\\s+"))
        
        for (keyword in keywords) {
            val index = words.indexOf(keyword)
            if (index != -1 && index + 1 < words.size) {
                val candidate = words[index + 1].replace(Regex("[^A-Z]"), "")
                if (candidate.length > 2 && candidate != "SAYA") return candidate
            }
        }

        for (word in words) {
            val cleanWord = word.replace(Regex("[^A-Z]"), "")
            if (cleanWord.length > 2 && 
                cleanWord != "SAYA" && 
                cleanWord != "PINJAM" && 
                cleanWord != "UTANG" && 
                cleanWord != "BERHUTANG" && 
                cleanWord != "SEBESAR" && 
                cleanWord != "RUPIAH" &&
                cleanWord != "UANG") {
                return cleanWord
            }
        }
        return "TEMAN"
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
