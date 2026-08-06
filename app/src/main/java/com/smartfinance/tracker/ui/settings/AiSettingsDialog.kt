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
        
        // 🔥 FIX: Menggunakan MaterialAlertDialogBuilder agar pop-up melengkung elegan dan sesuai dark mode
        val dialog = MaterialAlertDialogBuilder(context).setView(dialogBinding.root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Catatan: Nama-nama model AI ini adalah "Proper Nouns" (Merek), jadi tidak perlu diterjemahkan.
        val aiModelsDisplay = listOf("Groq: llama-3.3-70b", "OpenAI: gpt-4o", "Google: gemini-3.1-pro", "Anthropic: claude-3-opus")
        val aiModelsValue = listOf("llama-3.3-70b-versatile", "gpt-4o", "gemini-3.1-pro-preview", "claude-3-opus-20240229")

        dialogBinding.spinnerAiModel.adapter = android.widget.ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, aiModelsDisplay)
        
        val savedModel = prefs.getString("ai_model", "llama-3.3-70b-versatile")
        val selectedIndex = aiModelsValue.indexOf(savedModel).takeIf { it >= 0 } ?: 0
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
                
            // 🔥 FIX: Translasi dinamis untuk notifikasi berhasil disimpan
            Snackbar.make(viewToSnackbar, context.getString(R.string.api_config_saved_toast), Snackbar.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        dialog.show()
    }
}
