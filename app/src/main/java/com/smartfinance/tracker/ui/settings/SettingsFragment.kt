package com.smartfinance.tracker.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.smartfinance.tracker.MainActivity
import com.smartfinance.tracker.R
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
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SettingsViewModel
    private var isUserInteracting = false

    private val formatRp = NumberFormat.getCurrencyInstance(Locale("id", "ID"))

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
                
                Snackbar.make(binding.root, "✅ Database di-load!", Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {}
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
                    
                    withContext(Dispatchers.Main) { Snackbar.make(binding.root, "✅ Backup CSV disimpan!", Snackbar.LENGTH_LONG).show() }
                } catch (e: Exception) {}
            }
        }
    }

    // PELUNCUR UNTUK MENYIMPAN FILE PDF
    private val exportPdfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { fileUri ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    generatePdfReport(fileUri)
                    withContext(Dispatchers.Main) { Snackbar.make(binding.root, "✅ Laporan PDF berhasil dibuat!", Snackbar.LENGTH_LONG).show() }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { Snackbar.make(binding.root, "❌ Gagal membuat PDF", Snackbar.LENGTH_LONG).show() }
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

        setupThemeAndLanguageSpinners()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isBiometricEnabled.collect { isEnabled ->
                if (binding.switchBiometric.isChecked != isEnabled) binding.switchBiometric.isChecked = isEnabled
            }
        }

        binding.switchBiometric.setOnCheckedChangeListener { _, isChecked -> viewModel.setBiometricStatus(isChecked) }

        binding.menuFirebaseJson.setOnClickListener { filePickerLauncher.launch("application/json") }

        binding.menuExportCsv.setOnClickListener {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale("id", "ID"))
            exportCsvLauncher.launch("SmartFinance_Backup_${sdf.format(Date())}.csv")
        }

        // AKSI KLIK UNTUK PDF
        binding.menuExportPdf.setOnClickListener {
            val sdf = SimpleDateFormat("yyyyMMdd", Locale("id", "ID"))
            exportPdfLauncher.launch("SmartFinance_Report_${sdf.format(Date())}.pdf")
        }

        binding.menuManageCategories.setOnClickListener { CategoryManagerDialog().show(parentFragmentManager, "CategoryManagerDialog") }
        binding.menuBudgeting.setOnClickListener { com.smartfinance.tracker.ui.budget.BudgetManagerDialog().show(parentFragmentManager, "BudgetManagerDialog") }
        binding.menuRecurringTx.setOnClickListener { RecurringTxListDialog().show(parentFragmentManager, "RecurringTxListDialog") }

        setupAiDialogs(prefs)
    }

    // FUNGSI INTI PEMBUAT PDF
    private suspend fun generatePdfReport(fileUri: android.net.Uri) {
        val db = FirebaseManager.getFirestore()
        // Ambil 100 transaksi terbaru untuk menghemat memori saat generate PDF
        val txSnap = db.collection("transactions").orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(100).get().await()
        
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Ukuran A4 standar
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        
        val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true; color = Color.BLACK }
        val headerPaint = Paint().apply { textSize = 12f; isFakeBoldText = true; color = Color.BLACK }
        val textPaint = Paint().apply { textSize = 10f; color = Color.DKGRAY }
        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }

        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale("id", "ID"))
        var startY = 50f
        
        // Judul Dokumen
        canvas.drawText("Laporan Transaksi Smart Finance (100 Terbaru)", 50f, startY, titlePaint)
        startY += 20f
        canvas.drawText("Dicetak pada: ${sdf.format(Date())}", 50f, startY, textPaint)
        startY += 40f

        // Header Tabel
        canvas.drawText("Tanggal", 50f, startY, headerPaint)
        canvas.drawText("Kategori", 150f, startY, headerPaint)
        canvas.drawText("Catatan", 280f, startY, headerPaint)
        canvas.drawText("Nominal", 450f, startY, headerPaint)
        startY += 10f
        canvas.drawLine(50f, startY, 545f, startY, linePaint)
        startY += 20f

        // Isi Tabel
        for (doc in txSnap.documents) {
            val date = sdf.format(Date(doc.getLong("timestamp") ?: 0L))
            val cat = doc.getString("categoryName") ?: "-"
            var note = doc.getString("note") ?: "-"
            if (note.length > 25) note = note.substring(0, 22) + "..." // Truncate text panjang
            
            val type = doc.getString("type") ?: ""
            val amt = doc.getDouble("amount") ?: 0.0
            val prefix = if (type == "EXPENSE") "-" else "+"
            val formattedAmt = "$prefix${formatRp.format(amt)}"

            canvas.drawText(date, 50f, startY, textPaint)
            canvas.drawText(cat, 150f, startY, textPaint)
            canvas.drawText(note, 280f, startY, textPaint)
            
            textPaint.color = if (type == "EXPENSE") Color.RED else Color.parseColor("#006400") // Hijau gelap
            canvas.drawText(formattedAmt, 450f, startY, textPaint)
            textPaint.color = Color.DKGRAY // Kembalikan ke warna asli
            
            startY += 20f
            
            // Paginasi: Hentikan jika kertas penuh
            if (startY > 800f) {
                canvas.drawText("... (Lanjut ke halaman berikutnya - Tidak didukung pada versi ini)", 50f, startY, textPaint)
                break
            }
        }
        
        pdfDocument.finishPage(page)
        requireContext().contentResolver.openOutputStream(fileUri)?.use { os ->
            pdfDocument.writeTo(os)
        }
        pdfDocument.close()
    }

    private fun setupThemeAndLanguageSpinners() {
        val themeNames = listOf(getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark))
        val themeValues = listOf(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES)
        
        binding.spinnerTheme.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, themeNames)
        val currentThemeIndex = themeValues.indexOf(viewModel.themeMode.value).takeIf { it >= 0 } ?: 0
        binding.spinnerTheme.setSelection(currentThemeIndex)

        val langNames = listOf("🇮🇩 Indonesia", "🇬🇧 English")
        val langValues = listOf("id", "en")
        
        binding.spinnerLanguage.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, langNames)
        val currentLangIndex = langValues.indexOf(viewModel.appLanguage.value).takeIf { it >= 0 } ?: 0
        binding.spinnerLanguage.setSelection(currentLangIndex)

        binding.spinnerTheme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isUserInteracting) return
                viewModel.setThemeMode(themeValues[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isUserInteracting) return
                val selectedLang = langValues[position]
                viewModel.setLanguage(selectedLang)
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(selectedLang))
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.spinnerTheme.setOnTouchListener { _, _ -> isUserInteracting = true; false }
        binding.spinnerLanguage.setOnTouchListener { _, _ -> isUserInteracting = true; false }
    }

    private fun setupAiDialogs(prefs: android.content.SharedPreferences) {
        binding.menuApiConfig.setOnClickListener {
            val dialogBinding = DialogApiConfigBinding.inflate(layoutInflater)
            val dialog = AlertDialog.Builder(requireContext()).setView(dialogBinding.root).create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            val aiModelsDisplay = listOf("Groq: llama-3.3-70b", "OpenAI: gpt-4o", "Google: gemini-3.1-pro", "Anthropic: claude-3-opus")
            val aiModelsValue = listOf("llama-3.3-70b-versatile", "gpt-4o", "gemini-3.1-pro-preview", "claude-3-opus-20240229")

            dialogBinding.spinnerAiModel.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, aiModelsDisplay)
            val savedModel = prefs.getString("ai_model", "llama-3.3-70b-versatile")
            val selectedIndex = aiModelsValue.indexOf(savedModel).takeIf { it >= 0 } ?: 0
            dialogBinding.spinnerAiModel.setSelection(selectedIndex)
            
            dialogBinding.etApiKey.setText(prefs.getString("ai_api_key", prefs.getString("groq_key_override", "")))

            dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
            dialogBinding.btnSave.setOnClickListener {
                prefs.edit().putString("ai_model", aiModelsValue[dialogBinding.spinnerAiModel.selectedItemPosition])
                    .putString("ai_api_key", dialogBinding.etApiKey.text.toString().trim()).apply()
                (requireActivity() as? MainActivity)?.reinitializeFirebase()
                Snackbar.make(binding.root, "AI Config Saved!", Snackbar.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            dialog.show()
        }

        binding.menuExpertMode.setOnClickListener {
            val dialogBinding = DialogExpertModeBinding.inflate(layoutInflater)
            val dialog = AlertDialog.Builder(requireContext()).setView(dialogBinding.root).create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialogBinding.etPrompt.setText(prefs.getString("expert_system_prompt", AIClient.DEFAULT_PROMPT))
            dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
            dialogBinding.btnReset.setOnClickListener {
                prefs.edit().remove("expert_system_prompt").apply()
                dialog.dismiss()
            }
            dialogBinding.btnSave.setOnClickListener {
                prefs.edit().putString("expert_system_prompt", dialogBinding.etPrompt.text.toString()).apply()
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
