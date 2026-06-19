package com.smartfinance.tracker.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.firestore.FirebaseFirestore
import com.smartfinance.tracker.R
import com.smartfinance.tracker.ai.GroqClient // Jangan diubah dulu sebelum file GroqClient di-rename
import com.smartfinance.tracker.ui.category.CategoryManagerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment() {

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(it)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val jsonString = reader.use { r -> r.readText() }
                
                requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
                    .edit().putString("custom_firebase_json", jsonString).apply()
                
                Toast.makeText(requireContext(), "Firebase JSON dimuat! Harap restart aplikasi.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Gagal membaca file JSON.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val exportCsvLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { fileUri ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val db = FirebaseFirestore.getInstance()
                    val txSnap = db.collection("transactions").orderBy("timestamp").get().await()
                    
                    val sb = StringBuilder()
                    sb.append("Tanggal,Catatan,Kategori,Tipe,Nominal\n")
                    val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale("id", "ID"))
                    
                    for (doc in txSnap.documents) {
                        val date = sdf.format(Date(doc.getLong("timestamp") ?: 0L))
                        val note = (doc.getString("note") ?: "").replace(",", " ") // Hindari bentrok koma CSV
                        val cat = doc.getString("categoryName") ?: ""
                        val type = doc.getString("type") ?: ""
                        val amt = doc.getDouble("amount") ?: 0.0
                        sb.append("${date},${note},${cat},${type},${amt}\n")
                    }
                    
                    requireContext().contentResolver.openOutputStream(fileUri)?.use { os ->
                        os.write(sb.toString().toByteArray())
                    }
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "✅ Backup CSV berhasil disimpan!", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "❌ Gagal Export: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        val prefs = requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        val db = FirebaseFirestore.getInstance()

        // 🔥 FITUR BARU: MENU KONFIGURASI MULTI-AI
        view.findViewById<MaterialCardView>(R.id.menuApiConfig).setOnClickListener {
            val context = requireContext()
            val density = context.resources.displayMetrics.density

            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt())
            }

            layout.addView(TextView(context).apply {
                text = "Pilih Mesin AI Server:"
                setTextColor(Color.parseColor("#64748B"))
                textSize = 13f
                setPadding(0, 0, 0, (8 * density).toInt())
            })

            // Daftar Model AI Super Lengkap
            val aiModelsDisplay = listOf(
                "Groq: llama-3.3-70b-versatile (Super Cepat/Gratis)",
                "Groq: mixtral-8x7b-32768 (Cepat/Gratis)",
                "Groq: gemma2-9b-it (Ringan/Gratis)",
                "OpenAI: gpt-4o (Paling Cerdas/Pro)",
                "OpenAI: gpt-4-turbo (Cerdas/Pro)",
                "OpenAI: gpt-3.5-turbo (Standar/Pro)",
                "Google: gemini-1.5-pro (Super Cerdas/Pro)",
                "Google: gemini-1.5-flash (Cepat/Pro & Gratis Tier)",
                "Anthropic: claude-3-opus-20240229 (Super Cerdas/Pro)",
                "Anthropic: claude-3-sonnet-20240229 (Cerdas/Pro)",
                "Anthropic: claude-3-haiku-20240307 (Cepat/Pro)",
                "DeepSeek: deepseek-chat (Cerdas/Sangat Murah)"
            )
            
            val aiModelsValue = listOf(
                "llama-3.3-70b-versatile",
                "mixtral-8x7b-32768",
                "gemma2-9b-it",
                "gpt-4o",
                "gpt-4-turbo",
                "gpt-3.5-turbo",
                "gemini-1.5-pro",
                "gemini-1.5-flash",
                "claude-3-opus-20240229",
                "claude-3-sonnet-20240229",
                "claude-3-haiku-20240307",
                "deepseek-chat"
            )

            val spinnerModel = Spinner(context)
            val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, aiModelsDisplay)
            spinnerModel.adapter = adapter
            
            // Set ke model yang pernah disimpan (default llama-3.3)
            val savedModel = prefs.getString("ai_model", "llama-3.3-70b-versatile")
            val selectedIndex = aiModelsValue.indexOf(savedModel).takeIf { it >= 0 } ?: 0
            spinnerModel.setSelection(selectedIndex)
            
            layout.addView(spinnerModel)

            layout.addView(TextView(context).apply {
                text = "Kunci API (API Key):"
                setTextColor(Color.parseColor("#64748B"))
                textSize = 13f
                setPadding(0, (16 * density).toInt(), 0, (8 * density).toInt())
            })

            val etInput = EditText(context).apply { 
                hint = "Masukkan API Key yang sesuai..." 
                // Logika Migrasi: Ambil dari ai_api_key, jika kosong ambil dari groq_key_override lama
                val savedKey = prefs.getString("ai_api_key", prefs.getString("groq_key_override", ""))
                setText(savedKey)
                textSize = 14f
            }
            layout.addView(etInput)

            AlertDialog.Builder(context)
                .setTitle("⚙️ Konfigurasi Mesin AI")
                .setView(layout)
                .setPositiveButton("Simpan") { d, _ ->
                    val selectedValue = aiModelsValue[spinnerModel.selectedItemPosition]
                    prefs.edit()
                        .putString("ai_model", selectedValue)
                        .putString("ai_api_key", etInput.text.toString().trim())
                        .apply()
                    Toast.makeText(context, "Konfigurasi Mesin AI Tersimpan!", Toast.LENGTH_LONG).show()
                    d.dismiss()
                }.setNegativeButton("Batal", null).show()
        }

        view.findViewById<MaterialCardView>(R.id.menuFirebaseJson).setOnClickListener {
            filePickerLauncher.launch("application/json")
        }

        view.findViewById<MaterialCardView>(R.id.menuExpertMode).setOnClickListener {
            val etPrompt = EditText(requireContext()).apply {
                setText(prefs.getString("expert_system_prompt", GroqClient.DEFAULT_PROMPT))
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

        view.findViewById<MaterialCardView>(R.id.menuManageCategories).setOnClickListener {
            CategoryManagerDialog().show(parentFragmentManager, "CategoryManagerDialog")
        }

        view.findViewById<MaterialCardView>(R.id.menuExportCsv).setOnClickListener {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale("id", "ID"))
            val fileName = "SmartFinance_Backup_${sdf.format(Date())}.csv"
            exportCsvLauncher.launch(fileName)
        }

        view.findViewById<MaterialCardView>(R.id.menuRecurringTx)?.setOnClickListener {
            RecurringTxListDialog().show(parentFragmentManager, "RecurringTxListDialog")
        }

        val switchBiometric = view.findViewById<SwitchMaterial>(R.id.switchBiometric)
        switchBiometric.isChecked = prefs.getBoolean("use_biometric", false)
        
        switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("use_biometric", isChecked).apply()
            db.collection("app_config").document("security").set(hashMapOf("use_biometric" to isChecked))
            val status = if (isChecked) "Diaktifkan & Disimpan ke Cloud" else "Dimatikan"
            Toast.makeText(requireContext(), "Keamanan Biometrik $status", Toast.LENGTH_SHORT).show()
        }

        return view
    }
}
