package com.smartfinance.tracker.ui.settings

import android.app.AlertDialog
import android.content.Context
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
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SettingsViewModel
    private var isUserInteracting = false

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

        // 🔥 FIX: Panggilan ke BottomSheet yang baru dibuat
        binding.menuExportReport.setOnClickListener { 
            ExportBottomSheet().show(parentFragmentManager, "ExportBottomSheet") 
        }

        binding.menuManageCategories.setOnClickListener { CategoryManagerDialog().show(parentFragmentManager, "CategoryManagerDialog") }
        binding.menuBudgeting.setOnClickListener { com.smartfinance.tracker.ui.budget.BudgetManagerDialog().show(parentFragmentManager, "BudgetManagerDialog") }
        binding.menuRecurringTx.setOnClickListener { RecurringTxListDialog().show(parentFragmentManager, "RecurringTxListDialog") }

        setupAiDialogs(prefs)
    }

    private fun setupThemeAndLanguageSpinners() {
        val themeNames = listOf(getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark))
        val themeValues = listOf(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES)
        
        val themeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, themeNames)
        themeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTheme.adapter = themeAdapter
        
        val currentThemeIndex = themeValues.indexOf(viewModel.themeMode.value).takeIf { it >= 0 } ?: 0
        binding.spinnerTheme.setSelection(currentThemeIndex)

        val langNames = listOf("🇮🇩 Indonesia", "🇬🇧 English")
        val langValues = listOf("id", "en")
        
        val langAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, langNames)
        langAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLanguage.adapter = langAdapter
        
        val currentLangIndex = langValues.indexOf(viewModel.appLanguage.value).takeIf { it >= 0 } ?: 0
        binding.spinnerLanguage.setSelection(currentLangIndex)

        binding.spinnerTheme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isUserInteracting) return
                val selectedTheme = themeValues[position]
                
                requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
                    .edit().putInt("app_theme", selectedTheme).commit()
                viewModel.setThemeMode(selectedTheme)
                
                viewLifecycleOwner.lifecycleScope.launch {
                    kotlinx.coroutines.delay(150)
                    AppCompatDelegate.setDefaultNightMode(selectedTheme)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isUserInteracting) return
                val selectedLang = langValues[position]
                
                requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
                    .edit().putString("app_language", selectedLang).commit()
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
