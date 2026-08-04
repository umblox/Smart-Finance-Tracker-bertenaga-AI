package com.smartfinance.tracker.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                Toast.makeText(requireContext(), getString(R.string.dialog_google_fail), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val exportBackupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            LocalBackupUtil.exportDataToUri(requireContext(), it)
        }
    }

    private val importBackupLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            LocalBackupUtil.importDataFromUri(requireContext(), it)
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
                    .setTitle(getString(R.string.settings_drive_title))
                    .setMessage("${getString(R.string.dialog_google_connected)} ${account.email}")
                    .setPositiveButton(getString(R.string.action_logout)) { _, _ ->
                        GoogleSignIn.getClient(requireContext(), GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
                        updateDriveUi(false)
                    }
                    .setNegativeButton(getString(R.string.action_cancel), null).show()
            }
        }

        binding.btnDriveBackup.setOnClickListener { performBackup() }
        binding.btnDriveRestore.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.dialog_restore_title))
                .setMessage(getString(R.string.dialog_restore_warning))
                .setPositiveButton(getString(R.string.settings_drive_restore_btn)) { _, _ -> performRestore() }
                .setNegativeButton(getString(R.string.action_cancel), null).show()
        }
        
        binding.menuExportReport.setOnClickListener { ExportBottomSheet().show(parentFragmentManager, "ExportBottomSheet") }
        binding.menuManageCategories.setOnClickListener { CategoryManagerDialog().show(parentFragmentManager, "CategoryManagerDialog") }
        binding.menuBudgeting.setOnClickListener { com.smartfinance.tracker.ui.budget.BudgetManagerDialog().show(parentFragmentManager, "BudgetManagerDialog") }
        binding.menuRecurringTx.setOnClickListener { RecurringTxListDialog().show(parentFragmentManager, "RecurringTxListDialog") }

        binding.menuLocalBackup.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.dialog_local_title))
                .setItems(arrayOf(getString(R.string.dialog_local_export), getString(R.string.dialog_local_import))) { _, which ->
                    if (which == 0) {
                        val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale("id", "ID"))
                        exportBackupLauncher.launch("SmartFinance_Backup_${sdf.format(Date())}.json")
                    } else {
                        importBackupLauncher.launch(arrayOf("application/json", "*/*"))
                    }
                }
                .show()
        }

        binding.menuApiConfig.setOnClickListener { AiSettingsDialog.showApiConfig(requireContext(), layoutInflater, prefs, binding.root) }
    }

    private fun updateDriveUi(isSignedIn: Boolean) {
        if (isSignedIn) {
            val prefs = requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
            val lastSync = prefs.getString("last_sync_time", getString(R.string.status_waiting_sync))
            
            binding.tvDriveStatusSubtitle.text = lastSync
            binding.tvDriveStatusSubtitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.income_green))
            binding.layoutDriveActions.visibility = View.VISIBLE
        } else {
            binding.tvDriveStatusSubtitle.text = getString(R.string.status_tap_login)
            binding.tvDriveStatusSubtitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.expense_red))
            binding.layoutDriveActions.visibility = View.GONE
        }
    }

    private fun checkAndPromptRestore() {
        lifecycleScope.launch {
            binding.tvDriveStatusSubtitle.text = getString(R.string.status_checking_cloud)
            val fileId = GoogleDriveManager.checkBackupFileId(requireContext())
            if (fileId != null) {
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.dialog_cloud_found_title))
                    .setMessage(getString(R.string.dialog_cloud_found_desc))
                    .setPositiveButton(getString(R.string.action_yes_restore)) { _, _ -> performRestore() }
                    .setNegativeButton(getString(R.string.action_ignore_overwrite)) { _, _ -> performBackup() }
                    .setCancelable(false)
                    .show()
                updateDriveUi(true)
            } else {
                performBackup() 
            }
        }
    }

    private fun performBackup() {
        lifecycleScope.launch {
            binding.tvDriveStatusSubtitle.text = getString(R.string.status_uploading)
            val json = BackupEngine.exportDbToJson()
            val success = GoogleDriveManager.uploadBackup(requireContext(), json)
            if (success) {
                val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
                val timeStr = sdf.format(Date())
                requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
                    .edit().putString("last_sync_time", "Manual Sync: $timeStr").apply()
                    
                Toast.makeText(requireContext(), getString(R.string.toast_backup_success), Toast.LENGTH_SHORT).show()
                updateDriveUi(true)
            } else {
                Toast.makeText(requireContext(), getString(R.string.toast_backup_fail), Toast.LENGTH_SHORT).show()
                binding.tvDriveStatusSubtitle.text = getString(R.string.status_sync_failed)
            }
        }
    }

    private fun performRestore() {
        lifecycleScope.launch {
            binding.tvDriveStatusSubtitle.text = getString(R.string.status_downloading)
            val json = GoogleDriveManager.downloadBackup(requireContext())
            if (json != null) {
                val success = BackupEngine.importJsonToDb(json)
                if (success) {
                    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
                    val timeStr = sdf.format(Date())
                    requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
                        .edit().putString("last_sync_time", "Manual Restore: $timeStr").apply()
                        
                    Toast.makeText(requireContext(), getString(R.string.toast_restore_success), Toast.LENGTH_SHORT).show()
                    updateDriveUi(true)
                } else {
                    Toast.makeText(requireContext(), getString(R.string.toast_restore_corrupt), Toast.LENGTH_SHORT).show()
                    binding.tvDriveStatusSubtitle.text = getString(R.string.status_sync_failed)
                }
            } else {
                Toast.makeText(requireContext(), getString(R.string.toast_download_fail), Toast.LENGTH_SHORT).show()
                binding.tvDriveStatusSubtitle.text = getString(R.string.status_sync_failed)
            }
        }
    }

    // 🔥 FIX: Fungsi Cerdas untuk Mewarnai Teks Dropdown secara Dinamis
    private fun createThemedAdapter(items: List<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                return view
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                return view
            }
        }.apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun setupThemeAndLanguageSpinners() {
        val themeNames = listOf(getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark))
        val themeValues = listOf(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES)
        
        // 🔥 FIX: Gunakan Custom Adapter
        binding.spinnerTheme.adapter = createThemedAdapter(themeNames)
        binding.spinnerTheme.setSelection(themeValues.indexOf(viewModel.themeMode.value).takeIf { it >= 0 } ?: 0)

        val langNames = listOf("🇮🇩 Indonesia", "🇬🇧 English")
        val langValues = listOf("id", "en")
        
        // 🔥 FIX: Gunakan Custom Adapter
        binding.spinnerLanguage.adapter = createThemedAdapter(langNames)
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
