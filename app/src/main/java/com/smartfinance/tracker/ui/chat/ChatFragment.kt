package com.smartfinance.tracker.ui.chat

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
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

    // Penampung tombol kustom baru agar bisa di-disable/enable saat AI berpikir
    private var customSendButton: com.google.android.material.card.MaterialCardView? = null

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

        chatAdapter = ChatAdapter(messageList)
        binding.rvChatHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvChatHistory.adapter = chatAdapter

        // =======================================================
        // ROMBAK TOTAL: TIMBULKAN & BULATKAN TOMBOL SEND (LOGOSEND)
        // =======================================================
        // 1. Sembunyikan tombol kotak bawaan XML lama
        binding.btnSend.visibility = View.GONE

        // 2. Buat MaterialCardView bulat premium penampung icon
        val density = requireContext().resources.displayMetrics.density
        val sizePx = (48 * density).toInt() // Ukuran ideal 48dp bulat

        customSendButton = com.google.android.material.card.MaterialCardView(requireContext()).apply {
            radius = sizePx / 2f // Garansi bulat lingkaran murni
            cardElevation = 14f  // Efek bayangan tegas memisahkan diri dari bg
            setCardBackgroundColor(Color.parseColor("#008080")) // Warna kebanggaan Teal
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                leftMargin = (8 * density).toInt()
            }
        }

        // 3. Masukkan gambar vector pesawat kertas bawaan framework Android core
        val ivSendIcon = ImageView(requireContext()).apply {
            setImageResource(android.R.drawable.ic_menu_send)
            setColorFilter(Color.WHITE)
            setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        customSendButton?.addView(ivSendIcon)

        // 4. Masukkan paksa ke kontainer input (di sebelah kolom teks etMessage)
        val inputContainer = binding.etMessage.parent as? LinearLayout
        inputContainer?.addView(customSendButton)

        val prefs = requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        val savedChat = prefs.getString("chat_history_backup_v4", "")
        
        if (!savedChat.isNullOrEmpty()) {
            loadBackupToAdapter(savedChat)
        } else {
            messageList.add(ChatMessage("Halo Umam! Saya asisten keuangan AI kamu. Silakan ketik transaksi seperti 'beli rokok 20000' atau 'gajian 2300000'.", false))
            chatAdapter.notifyDataSetChanged()
        }

        // 5. Pautkan logika kirim ke tombol premium yang baru
        customSendButton?.setOnClickListener {
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
        
        // Disable tombol kustom & redupkan keanggunannya saat AI bekerja
        customSendButton?.isEnabled = false 
        customSendButton?.alpha = 0.5f

        messageList.add(ChatMessage("AI sedang berpikir...", false))
        chatAdapter.notifyItemInserted(messageList.size - 1)
        binding.rvChatHistory.scrollToPosition(messageList.size - 1)

        lifecycleScope.launch {
            val cleanNarrationResponse = geminiClient.sendMessageToAI(message)
            
            if (messageList.isNotEmpty()) {
                messageList.removeAt(messageList.size - 1)
            }

            messageList.add(ChatMessage(cleanNarrationResponse.trim(), false))
            chatAdapter.notifyDataSetChanged()
            binding.rvChatHistory.post { binding.rvChatHistory.scrollToPosition(messageList.size - 1) }
            
            // Kembalikan status tombol kustom setelah data turun dari server
            customSendButton?.isEnabled = true
            customSendButton?.alpha = 1.0f
            
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
