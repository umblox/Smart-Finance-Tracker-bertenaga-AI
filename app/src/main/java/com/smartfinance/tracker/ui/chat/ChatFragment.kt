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
import com.smartfinance.tracker.ai.FinancialAssistant
import com.smartfinance.tracker.ai.GroqClient
import com.smartfinance.tracker.data.model.ChatMessage
import com.smartfinance.tracker.data.remote.FirebaseSyncManager
import com.smartfinance.tracker.databinding.FragmentChatBinding
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var groqClient: GroqClient
    private lateinit var assistant: FinancialAssistant
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
        assistant = FinancialAssistant(contextRef)
        groqClient = GroqClient(contextRef, assistant)
        syncManager = FirebaseSyncManager(contextRef)

        chatAdapter = ChatAdapter(messageList)
        binding.rvChatHistory.layoutManager = LinearLayoutManager(contextRef)
        binding.rvChatHistory.adapter = chatAdapter

        // Konfigurasi tombol send bawaan visualisasi kustom...
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
        if (!savedChat.isNullOrEmpty()) { loadBackupToAdapter(savedChat) }

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

            if (rawResponse.contains("CONFIRMATION_REQUIRED")) {
                // Render Mode Defensif Interaktif Opsi Jalur Ganda
                try {
                    val json = JSONObject(rawResponse.trim().removePrefix("
```json").removePrefix("```").removeSuffix("```").trim())
                    val txArray = json.optJSONArray("transactions")
                    val item = txArray?.getJSONObject(0)
                    val amount = item?.optDouble("amount", 0.0) ?: 0.0
                    val name = item?.optString("contact_name", "Seseorang")?.uppercase(Locale.ROOT) ?: "SESEORANG"

                    injectConfirmationButtonsToChat(name, amount)
                } catch (e: Exception) {
                    messageList.add(ChatMessage("Mohon ulangi kalimat Anda dengan lebih jelas, Mam.", false))
                }
            } else {
                // Jalur normal asisten cerdas
                messageList.add(ChatMessage(rawResponse.trim(), false))
            }

            chatAdapter.notifyDataSetChanged()
            binding.rvChatHistory.post { binding.rvChatHistory.scrollToPosition(messageList.size - 1) }
            binding.btnSend.isEnabled = true
            binding.btnSend.alpha = 1.0f

            // Save Backup preferensi lokal
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
        val formattedAmount = "Rp " + String.format("%,.0f", amount)
        messageList.add(ChatMessage("🤔 Kalimat Anda sedikit membingungkan sistem AI. Tolong pilih opsi arah transaksi yang benar di bawah ini agar Dashboard Anda akurat, Mam:", false))
        
        // Pemicu tombol aksi lokal
        chatAdapter.notifyDataSetChanged()

        // Munculkan dialog interseptor kaku di layar chat
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 20, 30, 20)
        }
        val btn1 = Button(requireContext()).apply { 
            text = "1. Anda Berhutang ke $name ($formattedAmount)"
            setOnClickListener {
                lifecycleScope.launch {
                    assistant.executeDirectDebtRecord(name, amount, isReceivable = false, timestampValue = System.currentTimeMillis())
                    Toast.makeText(context, "Berhasil dicatat sebagai Hutang (Kas Masuk Dashboard)!", Toast.LENGTH_SHORT).show()
                    messageList.add(ChatMessage("✅ Berhasil tercatat: Anda memiliki HUTANG kepada $name sebesar $formattedAmount.", false))
                    chatAdapter.notifyDataSetChanged()
                }
            }
        }
        val btn2 = Button(requireContext()).apply { 
            text = "2. $name Berhutang ke Anda ($formattedAmount)"
            setOnClickListener {
                lifecycleScope.launch {
                    assistant.executeDirectDebtRecord(name, amount, isReceivable = true, timestampValue = System.currentTimeMillis())
                    Toast.makeText(context, "Berhasil dicatat sebagai Piutang (Kas Keluar Dashboard)!", Toast.LENGTH_SHORT).show()
                    messageList.add(ChatMessage("✅ Berhasil tercatat: Anda memiliki PIUTANG kepada $name sebesar $formattedAmount.", false))
                    chatAdapter.notifyDataSetChanged()
                }
            }
        }
        container.addView(btn1)
        container.addView(btn2)
        
        AlertDialog.Builder(requireContext()).apply {
            setTitle("⚖️ Konfirmasi Arah Transaksi")
            setView(container)
            setCancelable(false)
            setNegativeButton("Batal Chat") { d, _ -> d.dismiss() }
            show()
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
