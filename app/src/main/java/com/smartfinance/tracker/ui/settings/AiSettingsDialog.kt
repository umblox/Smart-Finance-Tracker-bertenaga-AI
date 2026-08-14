package com.smartfinance.tracker.ui.settings

import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.smartfinance.tracker.R
import com.smartfinance.tracker.databinding.DialogApiConfigBinding

object AiSettingsDialog {

    fun showApiConfig(context: Context, inflater: LayoutInflater, prefs: SharedPreferences, viewToSnackbar: android.view.View) {
        val dialogBinding = DialogApiConfigBinding.inflate(inflater)
        
        val dialog = MaterialAlertDialogBuilder(context).setView(dialogBinding.root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 🔥 FIX: Llama 70B diganti menjadi Groq GPT OSS 120B (Sesuai rekomendasi & ketersediaan Groq)
        val aiModelsDisplay = listOf("Groq: gpt-oss-120b", "OpenAI: gpt-4o", "Google: gemini-3.1-pro", "Anthropic: claude-3-opus")
        val aiModelsValue = listOf("openai/gpt-oss-120b", "gpt-4o", "gemini-3.1-pro-preview", "claude-3-opus-20240229")

        dialogBinding.spinnerAiModel.adapter = android.widget.ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, aiModelsDisplay)
        
        // Cek fallback jika model sebelumnya llama, pindahkan ke openai/gpt-oss-120b
        val savedModel = prefs.getString("ai_model", "openai/gpt-oss-120b")
        val mappedModel = if (savedModel == "llama-3.3-70b-versatile") "openai/gpt-oss-120b" else savedModel
        
        val selectedIndex = aiModelsValue.indexOf(mappedModel).takeIf { it >= 0 } ?: 0
        dialogBinding.spinnerAiModel.setSelection(selectedIndex)
        
        dialogBinding.etApiKey.setText(prefs.getString("ai_api_key", prefs.getString("groq_key_override", "")))
        
        dialogBinding.etUserName.setText(prefs.getString("user_name", ""))

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnSave.setOnClickListener {
            val inputName = dialogBinding.etUserName.text.toString().trim()
            val editor = prefs.edit()
            
            editor.putString("ai_model", aiModelsValue[dialogBinding.spinnerAiModel.selectedItemPosition])
            editor.putString("ai_api_key", dialogBinding.etApiKey.text.toString().trim())
            
            if (inputName.isEmpty()) {
                editor.remove("user_name")
            } else {
                editor.putString("user_name", inputName)
            }
            
            editor.apply()
                
            Snackbar.make(viewToSnackbar, context.getString(R.string.api_config_saved_toast), Snackbar.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        dialog.show()
    }
}
