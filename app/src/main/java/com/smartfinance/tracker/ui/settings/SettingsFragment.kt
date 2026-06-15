package com.smartfinance.tracker.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.smartfinance.tracker.R
import com.smartfinance.tracker.ui.category.CategoryManagerDialog
import java.io.BufferedReader
import java.io.InputStreamReader

class SettingsFragment : Fragment() {

    // KUNCI SAKTI: Prompt Master Default AI Kita
    private val DEFAULT_AI_PROMPT = """
        Anda adalah Asisten Finansial Pribadi cerdas untuk Ikromul Umam (selalu panggil 'Mam').
        🗓️ WAKTU SAAT INI: {TODAY_DATE}
        
        [KATEGORI DATABASE]: \n{CAT_CONTEXT}
        [HUTANG SAYA]: \n{MY_DEBT_CONTEXT}
        [PIUTANG SAYA]: \n{OTHER_RECEIVABLE_CONTEXT}
        [50 TRANSAKSI TERAKHIR MAM]: \n{TX_CONTEXT}
        (GUNAKAN DATA TRANSAKSI DI ATAS HANYA UNTUK MENCARI TANGGAL/WAKTU. DILARANG MENGHITUNG TOTAL DARI SINI!)
        
        ATURAN INTERAKSI & KLASIFIKASI MUTLAK:
        1. PERTANYAAN TANGGAL: Cari barang di [50 TRANSAKSI TERAKHIR]. Kembalikan "CHAT_ONLY".
        2. PERMINTAAN LAPORAN: WAJIB set "report_type". Jika waktu spesifik, WAJIB "CUSTOM_RANGE".
        3. CATAT TRANSAKSI & KONFIRMASI: Jika barang meragukan, tanyakan opsi ke Mam di 'ai_response'.
        
        FORMAT JSON WAJIB:
        {
          "action_type": "CHAT_ONLY" | "TRANSACTION" | "DEBT_RECORD" | "DEBT_PAYMENT" | "VIEW_REPORT" | "VIEW_CATEGORIES",
          "ai_response": "Balasan luwes",
          "report_filter": { "report_type": "SUMMARY" | "TOP_EXPENSE" | "CATEGORY_BREAKDOWN" | "ITEM_DETAILS", "time_range": "TODAY" | "WEEKLY" | "MONTHLY" | "LAST_MONTH" | "YEARLY" | "CUSTOM_RANGE" | "ALL", "start_date": "dd-MM-yyyy", "end_date": "dd-MM-yyyy", "target_category": "Kategori", "target_keyword": "Keyword" },
          "transactions": [{ "amount": 0, "type": "EXPENSE" | "INCOME", "category_id": 15, "category_name": "Nama", "clean_note": "Barang", "contact_name": "Kontak", "debt_type": "DEBT" | "RECEIVABLE", "is_new_category": false, "parent_category_id": "ID", "transaction_date": "dd-MM-yyyy HH:mm" }]
        }
    """.trimIndent()

    // Engine File Picker untuk upload google-services.json
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(it)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val jsonString = reader.use { r -> r.readText() }
                
                // Simpan JSON mentah ke SharedPreferences untuk di-inject ke FirebaseApp saat restart
                requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
                    .edit().putString("custom_firebase_json", jsonString).apply()
                
                Toast.makeText(requireContext(), "Firebase JSON berhasil dimuat! Harap restart aplikasi.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Gagal membaca file JSON.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        val prefs = requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)

        // 1. MENU API GROQ
        view.findViewById<MaterialCardView>(R.id.menuApiGroq).setOnClickListener {
            val etInput = EditText(requireContext()).apply { 
                hint = "Token API..." 
                setText(prefs.getString("groq_key_override", ""))
            }
            AlertDialog.Builder(requireContext()).setTitle("Konfigurasi API Groq").setView(etInput)
                .setPositiveButton("Simpan") { d, _ ->
                    prefs.edit().putString("groq_key_override", etInput.text.toString().trim()).apply()
                    Toast.makeText(context, "API Key Groq Tersimpan!", Toast.LENGTH_SHORT).show()
                    d.dismiss()
                }.setNegativeButton("Batal", null).show()
        }

        // 2. MENU UPLOAD FIREBASE JSON (WHITE-LABELING)
        view.findViewById<MaterialCardView>(R.id.menuFirebaseJson).setOnClickListener {
            filePickerLauncher.launch("application/json")
        }

        // 3. 🔥 EXPERT MODE (PROMPT ENGINEERING) 🔥
        view.findViewById<MaterialCardView>(R.id.menuExpertMode).setOnClickListener {
            val etPrompt = EditText(requireContext()).apply {
                setText(prefs.getString("expert_system_prompt", DEFAULT_AI_PROMPT))
                textSize = 12f
                setTextColor(Color.parseColor("#334155"))
                setBackgroundColor(Color.parseColor("#F1F5F9"))
                setPadding(20, 20, 20, 20)
                gravity = android.view.Gravity.TOP
                minLines = 15
            }

            AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
                .setTitle("🧠 Expert Mode")
                .setMessage("Ubah instruksi kaku AI di bawah ini dengan hati-hati. Jika AI bertingkah aneh, tekan 'Reset ke Default'.")
                .setView(etPrompt)
                .setPositiveButton("Simpan Prompt") { d, _ ->
                    prefs.edit().putString("expert_system_prompt", etPrompt.text.toString()).apply()
                    Toast.makeText(context, "Prompt Expert Tersimpan!", Toast.LENGTH_SHORT).show()
                    d.dismiss()
                }
                .setNeutralButton("🔄 Reset ke Default") { d, _ ->
                    prefs.edit().remove("expert_system_prompt").apply()
                    Toast.makeText(context, "Prompt dikembalikan ke racikan Master!", Toast.LENGTH_SHORT).show()
                    d.dismiss()
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        // 4. KELOLA KATEGORI
        view.findViewById<MaterialCardView>(R.id.menuManageCategories).setOnClickListener {
            CategoryManagerDialog().show(parentFragmentManager, "CategoryManagerDialog")
        }

        // 5. EXPORT CSV
        view.findViewById<MaterialCardView>(R.id.menuExportCsv).setOnClickListener {
            Toast.makeText(requireContext(), "Fitur Export CSV sedang disiapkan untuk pembaruan berikutnya!", Toast.LENGTH_SHORT).show()
        }

        // 6. KEAMANAN BIOMETRIK
        val switchBiometric = view.findViewById<SwitchMaterial>(R.id.switchBiometric)
        switchBiometric.isChecked = prefs.getBoolean("use_biometric", false)
        switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("use_biometric", isChecked).apply()
            val status = if (isChecked) "Diaktifkan" else "Dimatikan"
            Toast.makeText(requireContext(), "Keamanan Biometrik $status", Toast.LENGTH_SHORT).show()
        }

        return view
    }
}
