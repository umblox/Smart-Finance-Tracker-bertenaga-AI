package com.smartfinance.tracker.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.smartfinance.tracker.R
import com.smartfinance.tracker.databinding.FragmentSettingsBinding
import com.smartfinance.tracker.ui.category.CategoryManagerDialog
import com.smartfinance.tracker.utils.BackupEngine
import com.smartfinance.tracker.utils.GoogleDriveManager
import com.smartfinance.tracker.utils.LocalBackupUtil
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SettingsViewModel
    private var isUserInteracting = false

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                updateDriveUi(account != null)
                checkAndPromptRestore()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Gagal terhubung ke Google", Toast.LENGTH_SHORT).show()
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

        // Cek Status Login Google Saat Ini
        val currentAccount = GoogleSignIn.getLastSignedInAccount(requireContext())
        updateDriveUi(currentAccount != null)

        binding.menuDriveAuth.setOnClickListener {
            val account = GoogleSignIn.getLastSignedInAccount(requireContext())
            if (account == null) {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
                    .build()
                val client = GoogleSignIn.getClient(requireContext(), gso)
                googleSignInLauncher.launch(client.signInIntent)
            } else {
                AlertDialog.Builder(requireContext())
                    .setTitle("Google Drive")
                    .setMessage("Akun terhubung: ${account.email}")
                    .setPositiveButton("Logout") { _, _ ->
                        GoogleSignIn.getClient(requireContext(), GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
                        updateDriveUi(false)
                    }
                    .setNegativeButton("Batal", null).show()
            }
        }

        binding.btnDriveBackup.setOnClickListener { performBackup() }
        binding.btnDriveRestore.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Restore Data")
                .setMessage("Tindakan ini akan menimpa seluruh data di HP Anda dengan data dari Cloud. Lanjutkan?")
                .setPositiveButton("Restore") { _, _ -> performRestore() }
                .setNegativeButton("Batal", null).show()
        }
        
        binding.menuExportReport.setOnClickListener { ExportBottomSheet().show(parentFragmentManager, "ExportBottomSheet") }
        binding.menuManageCategories.setOnClickListener { CategoryManagerDialog().show(parentFragmentManager, "CategoryManagerDialog") }
        binding.menuBudgeting.setOnClickListener { com.smartfinance.tracker.ui.budget.BudgetManagerDialog().show(parentFragmentManager, "BudgetManagerDialog") }
        binding.menuRecurringTx.setOnClickListener { RecurringTxListDialog().show(parentFragmentManager, "RecurringTxListDialog") }

        // 🔥 TAMBAHAN AKSI: Backup & Restore Lokal
        binding.menuLocalBackup.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("🛠️ Backup & Restore Lokal")
                .setItems(arrayOf("📤 Export Database ke HP", "📥 Import Database dari HP")) { _, which ->
                    if (which == 0) {
                        LocalBackupUtil.exportDatabase(requireContext())
                    } else {
                        LocalBackupUtil.importDatabase(requireContext())
                    }
                }
                .show()
        }

        binding.menuApiConfig.setOnClickListener { AiSettingsDialog.showApiConfig(requireContext(), layoutInflater, prefs, binding.root) }
        binding.menuExpertMode.setOnClickListener { AiSettingsDialog.showExpertMode(requireContext(), layoutInflater, prefs) }
    }

    private fun updateDriveUi(isSignedIn: Boolean) {
        if (isSignedIn) {
            binding.tvDriveStatusSubtitle.text = "Terhubung (Auto-Sync Aktif)"
            binding.tvDriveStatusSubtitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.income_green))
            binding.layoutDriveActions.visibility = View.VISIBLE
        } else {
            binding.tvDriveStatusSubtitle.text = "Ketuk untuk Login"
            binding.tvDriveStatusSubtitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.expense_red))
            binding.layoutDriveActions.visibility = View.GONE
        }
    }

    private fun checkAndPromptRestore() {
        lifecycleScope.launch {
            binding.tvDriveStatusSubtitle.text = "Memeriksa Cloud..."
            val fileId = GoogleDriveManager.checkBackupFileId(requireContext())
            if (fileId != null) {
                AlertDialog.Builder(requireContext())
                    .setTitle("☁️ Backup Ditemukan")
                    .setMessage("Ditemukan data backup di Google Drive Anda. Apakah Anda ingin mengunduh dan menimpa data lokal saat ini?")
                    .setPositiveButton("Ya, Restore") { _, _ -> performRestore() }
                    .setNegativeButton("Abaikan (Timpa Cloud)") { _, _ -> performBackup() }
                    .setCancelable(false)
                    .show()
                updateDriveUi(true)
            } else {
                performBackup() // Auto backup pertama kali jika belum ada
            }
        }
    }

    private fun performBackup() {
        lifecycleScope.launch {
            binding.tvDriveStatusSubtitle.text = "Mengunggah data..."
            val json = BackupEngine.exportDbToJson()
            val success = GoogleDriveManager.uploadBackup(requireContext(), json)
            if (success) {
                Toast.makeText(requireContext(), "Backup Selesai!", Toast.LENGTH_SHORT).show()
                updateDriveUi(true)
            } else {
                Toast.makeText(requireContext(), "Gagal Backup ke Cloud", Toast.LENGTH_SHORT).show()
                binding.tvDriveStatusSubtitle.text = "Gagal Sinkronisasi"
            }
        }
    }

    private fun performRestore() {
        lifecycleScope.launch {
            binding.tvDriveStatusSubtitle.text = "Mengunduh data..."
            val json = GoogleDriveManager.downloadBackup(requireContext())
            if (json != null) {
                val success = BackupEngine.importJsonToDb(json)
                if (success) {
                    Toast.makeText(requireContext(), "Restore Sukses!", Toast.LENGTH_SHORT).show()
                    updateDriveUi(true)
                } else {
                    Toast.makeText(requireContext(), "Data Backup Rusak!", Toast.LENGTH_SHORT).show()
                    binding.tvDriveStatusSubtitle.text = "Restore Gagal"
                }
            } else {
                Toast.makeText(requireContext(), "Gagal Mengunduh dari Cloud", Toast.LENGTH_SHORT).show()
                binding.tvDriveStatusSubtitle.text = "Gagal Sinkronisasi"
            }
        }
    }

    private fun setupThemeAndLanguageSpinners() {
        val themeNames = listOf(getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark))
        val themeValues = listOf(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES)
        binding.spinnerTheme.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, themeNames).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spinnerTheme.setSelection(themeValues.indexOf(viewModel.themeMode.value).takeIf { it >= 0 } ?: 0)

        val langNames = listOf("🇮🇩 Indonesia", "🇬🇧 English")
        val langValues = listOf("id", "en")
        binding.spinnerLanguage.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, langNames).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spinnerLanguage.setSelection(langValues.indexOf(viewModel.appLanguage.value).takeIf { it >= 0 } ?: 0)

        binding.spinnerTheme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isUserInteracting) return
                val selectedTheme = themeValues[position]
                requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE).edit().putInt("app_theme", selectedTheme).apply()
                viewModel.setThemeMode(selectedTheme)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        binding.spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isUserInteracting) return
                val selectedLang = langValues[position]
                requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE).edit().putString("app_language", selectedLang).apply()
                viewModel.setLanguage(selectedLang)
                viewLifecycleOwner.lifecycleScope.launch {
                    kotlinx.coroutines.delay(150)
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(selectedLang))
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        binding.spinnerTheme.setOnTouchListener { _, _ -> isUserInteracting = true; false }
        binding.spinnerLanguage.setOnTouchListener { _, _ -> isUserInteracting = true; false }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
