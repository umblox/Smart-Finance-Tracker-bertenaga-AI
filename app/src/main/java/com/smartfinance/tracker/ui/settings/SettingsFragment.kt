package com.smartfinance.tracker.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.smartfinance.tracker.MainActivity
import com.smartfinance.tracker.ai.AIClient
import com.smartfinance.tracker.databinding.DialogApiConfigBinding
import com.smartfinance.tracker.databinding.DialogExpertModeBinding
import com.smartfinance.tracker.databinding.FragmentSettingsBinding
import com.smartfinance.tracker.ui.category.CategoryManagerDialog
import com.smartfinance.tracker.utils.FirebaseManager
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

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SettingsViewModel

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(it)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val jsonString = reader.use { r -> r.readText() }
                
                requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
                    .edit().putString("custom_firebase_json", jsonString).commit()
                
                val activity = requireActivity()
                if (activity is MainActivity) activity.reinitializeFirebase()
                
                Toast.makeText(requireContext(), "✅ Database berhasil di-load otomatis!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "❌ Gagal membaca file JSON.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val exportCsvLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { fileUri ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val db = FirebaseManager.getFirestore()
                    val txSnap = db.collection("transactions").orderBy("timestamp").get().await()
                    
                    val sb = java.lang.StringBuilder()
                    sb.append("Tanggal,Catatan,Kategori,Tipe,Nominal\n")
                    val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale("id", "ID"))
                    
                    for (doc in txSnap.documents) {
                        val date = sdf.format(Date(doc.getLong("timestamp") ?: 0L))
                        val note = (doc.getString("note") ?: "").replace(",", " ")
                        val cat = doc.getString("categoryName") ?: ""
                        val type = doc.getString("type") ?: ""
                        val amt = doc.getDouble("amount") ?: 0.0
                        sb.append("${date},${note},${cat},${type},${amt}\n")
                    }
                    
                    requireContext().contentResolver.openOutputStream(fileUri)?.use { os ->
                        os.write(sb.toString().toByteArray())
                    }
                    
                    withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "✅ Backup CSV berhasil disimpan!", Toast.LENGTH_LONG).show() }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "❌ Gagal Export: ${e.message}", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        val prefs = requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)

        // Bind Biometric State
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isBiometricEnabled.collect { isEnabled ->
                if (binding.switchBiometric.isChecked != isEnabled) {
                    binding.switchBiometric.isChecked = isEnabled
                }
            }
        }

        binding.switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setBiometricStatus(isChecked)
            val status = if (isChecked) "Diaktifkan & Disimpan ke Cloud" else "Dimatikan"
            Toast.makeText(requireContext(), "Keamanan Biometrik $status", Toast.LENGTH_SHORT).show()
        }

        binding.menuFirebaseJson.setOnClickListener { filePickerLauncher.launch("application/json") }

        binding.menuExportCsv.setOnClickListener {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale("id", "ID"))
            val fileName = "SmartFinance_Backup_${sdf.format(Date())}.csv"
            exportCsvLauncher.launch(fileName)
        }

        binding.menuManageCategories.setOnClickListener { CategoryManagerDialog().show(parentFragmentManager, "CategoryManagerDialog") }
        binding.menuRecurringTx.setOnClickListener { RecurringTxListDialog().show(parentFragmentManager, "RecurringTxListDialog") }

        // --- Dialog Konfigurasi AI ---
        binding.menuApiConfig.setOnClickListener {
            val dialogBinding = DialogApiConfigBinding.inflate(layoutInflater)
            val dialog = AlertDialog.Builder(requireContext()).setView(dialogBinding.root).create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            val aiModelsDisplay = listOf(
                "Groq: llama-3.3-70b-versatile (Super Cepat/Gratis)",
                "Groq: mixtral-8x7b-32768 (Cepat/Gratis)",
                "Groq: gemma2-9b-it (Ringan/Gratis)",
                "OpenAI: gpt-4o (Paling Cerdas/Pro)",
                "OpenAI: gpt-4-turbo (Cerdas/Pro)",
                "OpenAI: gpt-3.5-turbo (Standar/Pro)",
                "Google: gemini-3.1-pro (Super Cerdas/Pro)",
                "Google: gemini-3.1-flash-lite (Cepat/Pro & Gratis Tier)",
                "Anthropic: claude-3-opus-20240229 (Super Cerdas/Pro)",
                "Anthropic: claude-3-sonnet-20240229 (Cerdas/Pro)",
                "Anthropic: claude-3-haiku-20240307 (Cepat/Pro)",
                "DeepSeek: deepseek-chat (Cerdas/Sangat Murah)"
            )
            val aiModelsValue = listOf(
                "llama-3.3-70b-versatile", "mixtral-8x7b-32768", "gemma2-9b-it", "gpt-4o", "gpt-4-turbo", "gpt-3.5-turbo",
                "gemini-3.1-pro-preview", "gemini-3.1-flash-lite", "claude-3-opus-20240229", "claude-3-sonnet-20240229", "claude-3-haiku-20240307", "deepseek-chat"
            )

            dialogBinding.spinnerAiModel.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, aiModelsDisplay)
            
            val savedModel = prefs.getString("ai_model", "llama-3.3-70b-versatile")
            val selectedIndex = aiModelsValue.indexOf(savedModel).takeIf { it >= 0 } ?: 0
            dialogBinding.spinnerAiModel.setSelection(selectedIndex)
            
            val savedKey = prefs.getString("ai_api_key", prefs.getString("groq_key_override", ""))
            dialogBinding.etApiKey.setText(savedKey)

            dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
            dialogBinding.btnSave.setOnClickListener {
                val selectedValue = aiModelsValue[dialogBinding.spinnerAiModel.selectedItemPosition]
                prefs.edit().putString("ai_model", selectedValue).putString("ai_api_key", dialogBinding.etApiKey.text.toString().trim()).apply()
                (requireActivity() as? MainActivity)?.reinitializeFirebase()
                Toast.makeText(context, "Konfigurasi Mesin AI Tersimpan!", Toast.LENGTH_LONG).show()
                dialog.dismiss()
            }
            dialog.show()
        }

        // --- Dialog Expert Mode ---
        binding.menuExpertMode.setOnClickListener {
            val dialogBinding = DialogExpertModeBinding.inflate(layoutInflater)
            val dialog = AlertDialog.Builder(requireContext()).setView(dialogBinding.root).create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            dialogBinding.etPrompt.setText(prefs.getString("expert_system_prompt", AIClient.DEFAULT_PROMPT))

            dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
            
            dialogBinding.btnReset.setOnClickListener {
                prefs.edit().remove("expert_system_prompt").apply()
                Toast.makeText(context, "Prompt dikembalikan ke racikan Master!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            
            dialogBinding.btnSave.setOnClickListener {
                prefs.edit().putString("expert_system_prompt", dialogBinding.etPrompt.text.toString()).apply()
                Toast.makeText(context, "Prompt Expert Tersimpan!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            dialog.show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
