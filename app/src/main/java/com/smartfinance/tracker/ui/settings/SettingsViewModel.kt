package com.smartfinance.tracker.ui.settings

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)

    private val _isBiometricEnabled = MutableStateFlow(prefs.getBoolean("use_biometric", false))
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled

    private val _themeMode = MutableStateFlow(prefs.getInt("app_theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
    val themeMode: StateFlow<Int> = _themeMode
    
    private val _appLanguage = MutableStateFlow(prefs.getString("app_language", "id") ?: "id")
    val appLanguage: StateFlow<String> = _appLanguage

    fun setBiometricStatus(enabled: Boolean) {
        prefs.edit().putBoolean("use_biometric", enabled).apply()
        _isBiometricEnabled.value = enabled
        // 🔥 Tidak lagi upload info Security ke Firestore
    }

    fun setThemeMode(mode: Int) {
        prefs.edit().putInt("app_theme", mode).apply()
        _themeMode.value = mode
        AppCompatDelegate.setDefaultNightMode(mode) 
    }

    fun setLanguage(langCode: String) {
        prefs.edit().putString("app_language", langCode).apply()
        _appLanguage.value = langCode
    }
}
