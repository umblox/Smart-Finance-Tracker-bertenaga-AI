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
            // Tarik data backup dari cloud firestore
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
            
            val isDebtQuery = upperMessage.contains("PINJAM") || upperMessage.contains("UTANG") || upperMessage.contains("BERHUTANG")
            val isPaymentQuery = upperMessage.contains("BAYAR") || upperMessage.contains("CICIL") || upperMessage.contains("LUNAS") || upperMessage.contains("TAGIH")

            var extractedAmount = 30000.0
            val numberRegex = Regex("\\d+")
            val match = numberRegex.find(upperMessage)
            if (match != null) {
                extractedAmount = match.value.toDoubleOrNull() ?: 30000.0
            }
            
            // 🔥 PERBAIKAN UTAMA: Nama diambil secara dinamis dari teks obrolan asli
            val name = dynamicContactNameExtractor(upperMessage)

            if (isDebtQuery && !isPaymentQuery) {
                val isSayaDipinjami = upperMessage.contains("MEMINJAM UANG SAYA") || 
                                      upperMessage.contains("BERHUTANG KEPADA SAYA") || 
                                      upperMessage.contains("PINJAM UANG SAYA")
                                      
                val isSayaNgutang = upperMessage.contains("SAYA MEMINJAM") || 
                                    upperMessage.contains("SAYA BERHUTANG") ||
                                    upperMessage.contains("SAYA NGUTANG")

                if (isSayaDipinjami) {
                    assistant.executeDirectDebtRecord(name, extractedAmount, true, System.currentTimeMillis())
                    messageList.add(ChatMessage("✅ [PIUTANG] Berhasil mencatat piutang Anda kepada $name sebesar ${formatRupiah.format(extractedAmount)}. Data langsung dialokasikan ke Dashboard dan Menu Tagihan!", false))
                } else if (isSayaNgutang) {
                    assistant.executeDirectDebtRecord(name, extractedAmount, false, System.currentTimeMillis())
                    messageList.add(ChatMessage("✅ [HUTANG] Berhasil mencatat hutang Anda kepada $name sebesar ${formatRupiah.format(extractedAmount)}. Data langsung dialokasikan ke Dashboard dan Menu Hutang!", false))
                } else {
                    injectConfirmationButtonsToChat(name, extractedAmount)
                }
            } else if (isPaymentQuery) {
                try {
                    val cleanJsonStr = rawResponse.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    
                    val customAiMessage = if (upperMessage.contains("LUNAS") || upperMessage.contains("MELUNASI")) {
                        "MELUNASI" 
                    } else {
                        "MENCICIL"
                    }
                    
                    assistant.parseAndExecuteRawAiResponse(rawResponse)
                    
                    if (customAiMessage == "MELUNASI") {
                        messageList.add(ChatMessage("✅ [PELUNASAN] Berhasil memproses pelunasan utang/piutang bersama $name. Status catatan di navigasi bawah kini berubah menjadi LUNAS ✅ dan Dashboard disesuaikan penuh!", false))
                    } else {
                        messageList.add(ChatMessage("✅ [CICILAN] Berhasil mencatat cicilan pembayaran bersama $name sebesar ${formatRupiah.format(extractedAmount)}. Angka sisa saldo dan Dashboard berhasil diperbarui!", false))
                    }
                } catch (e: Exception) {
                    assistant.parseAndExecuteRawAiResponse(rawResponse)
                    messageList.add(ChatMessage(rawResponse.trim(), false))
                }
            } else {
                if (rawResponse.contains("CONFIRMATION_REQUIRED")) {
                    messageList.add(ChatMessage("Mohon ulangi kalimat Anda dengan jelas, Mam.", false))
                } else {
                    messageList.add(ChatMessage(rawResponse.trim(), false))
                }
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
        messageList.add(ChatMessage("⚖️ **Validasi Nalar UI Pinjaman Terdeteksi:**\nSilakan tentukan keputusan akhir aliran dana agar angka Dashboard tidak terbalik, Mam:", false))
        chatAdapter.notifyDataSetChanged()

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 20, 30, 20)
        }
        
        val btn1 = Button(requireContext()).apply { 
            text = "1. Saya Berhutang ke $name ($formattedAmount)"
            setOnClickListener {
                lifecycleScope.launch {
                    assistant.executeDirectDebtRecord(name, amount, false, System.currentTimeMillis())
                    Toast.makeText(context, "Tercatat sebagai Hutang!", Toast.LENGTH_SHORT).show()
                    messageList.add(ChatMessage("✅ Berhasil diproses: Anda memiliki HUTANG kepada $name sebesar $formattedAmount.", false))
                    chatAdapter.notifyDataSetChanged()
                }
            }
        }
        
        val btn2 = Button(requireContext()).apply { 
            text = "2. $name Berhutang ke Saya ($formattedAmount)"
            setOnClickListener {
                lifecycleScope.launch {
                    assistant.executeDirectDebtRecord(name, amount, true, System.currentTimeMillis())
                    Toast.makeText(context, "Tercatat sebagai Piutang!", Toast.LENGTH_SHORT).show()
                    messageList.add(ChatMessage("✅ Berhasil diproses: Anda memiliki PIUTANG kepada $name sebesar $formattedAmount.", false))
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

    /**
     * 🔥 EKSTRAKTOR NAMA ASLI 100% DINAMIS BERBASIS POSISI KATA (KATA KUNCI BAHASA)
     */
    private fun dynamicContactNameExtractor(userText: String): String {
        val keywords = listOf("KEPADA", "DARI", "DENGAN", "OLEH", "UNTUK", "SAMA")
        val words = userText.split(Regex("\\s+"))
        
        // Cari kata setelah kata kunci bahasa
        for (keyword in keywords) {
            val index = words.indexOf(keyword)
            if (index != -1 && index + 1 < words.size) {
                val candidate = words[index + 1].replace(Regex("[^A-Z]"), "")
                if (candidate.length > 2 && candidate != "SAYA") return candidate
            }
        }

        // Jika tidak ada kata kunci, ambil kata pertama yang murni huruf (bukan instruksi utama saya/pinjam/nominal)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
