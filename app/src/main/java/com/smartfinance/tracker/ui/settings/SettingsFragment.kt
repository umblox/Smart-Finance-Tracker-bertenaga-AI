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
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.firestore.FirebaseFirestore
import com.smartfinance.tracker.R
import com.smartfinance.tracker.ai.GroqClient
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

    // 🔥 FITUR BARU: MESIN EXPORT CSV BACKUP
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

        view.findViewById<MaterialCardView>(R.id.menuFirebaseJson).setOnClickListener {
            filePickerLauncher.launch("application/json")
        }

        view.findViewById<MaterialCardView>(R.id.menuExpertMode).setOnClickListener {
            val etPrompt = EditText(requireContext()).apply {
                // 🔥 SINKRONISASI OTOMATIS: Membaca langsung dari DEFAULT_PROMPT di GroqClient
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

        // 🔥 MENGHIDUPKAN FITUR EXPORT CSV
        view.findViewById<MaterialCardView>(R.id.menuExportCsv).setOnClickListener {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale("id", "ID"))
            val fileName = "SmartFinance_Backup_${sdf.format(Date())}.csv"
            exportCsvLauncher.launch(fileName)
        }

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
