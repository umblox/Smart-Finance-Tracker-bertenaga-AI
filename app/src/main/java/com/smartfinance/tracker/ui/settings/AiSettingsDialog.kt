package com.smartfinance.tracker.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import com.google.android.material.snackbar.Snackbar
import com.smartfinance.tracker.ai.AIClient
import com.smartfinance.tracker.databinding.DialogApiConfigBinding
import com.smartfinance.tracker.databinding.DialogExpertModeBinding

object AiSettingsDialog {

    fun showApiConfig(context: Context, inflater: LayoutInflater, prefs: SharedPreferences, viewToSnackbar: android.view.View) {
        val dialogBinding = DialogApiConfigBinding.inflate(inflater)
        val dialog = AlertDialog.Builder(context).setView(dialogBinding.root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val aiModelsDisplay = listOf("Groq: llama-3.3-70b", "OpenAI: gpt-4o", "Google: gemini-3.1-pro", "Anthropic: claude-3-opus")
        val aiModelsValue = listOf("llama-3.3-70b-versatile", "gpt-4o", "gemini-3.1-pro-preview", "claude-3-opus-20240229")

        dialogBinding.spinnerAiModel.adapter = android.widget.ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, aiModelsDisplay)
        
        val savedModel = prefs.getString("ai_model", "llama-3.3-70b-versatile")
        val selectedIndex = aiModelsValue.indexOf(savedModel).takeIf { it >= 0 } ?: 0
        dialogBinding.spinnerAiModel.setSelection(selectedIndex)
        
        dialogBinding.etApiKey.setText(prefs.getString("ai_api_key", prefs.getString("groq_key_override", "")))

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnSave.setOnClickListener {
            prefs.edit()
                .putString("ai_model", aiModelsValue[dialogBinding.spinnerAiModel.selectedItemPosition])
                .putString("ai_api_key", dialogBinding.etApiKey.text.toString().trim())
                .apply()
                
            Snackbar.make(viewToSnackbar, "AI Config Saved!", Snackbar.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        dialog.show()
    }

    fun showExpertMode(context: Context, inflater: LayoutInflater, prefs: SharedPreferences) {
        val dialogBinding = DialogExpertModeBinding.inflate(inflater)
        val dialog = AlertDialog.Builder(context).setView(dialogBinding.root).create()
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
