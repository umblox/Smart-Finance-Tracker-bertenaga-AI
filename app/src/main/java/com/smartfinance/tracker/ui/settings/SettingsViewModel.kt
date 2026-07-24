package com.smartfinance.tracker.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartfinance.tracker.utils.FirebaseManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)

    private val _isBiometricEnabled = MutableStateFlow(prefs.getBoolean("use_biometric", false))
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled

    fun setBiometricStatus(enabled: Boolean) {
        prefs.edit().putBoolean("use_biometric", enabled).apply()
        _isBiometricEnabled.value = enabled
        
        viewModelScope.launch {
            try {
                val db = FirebaseManager.getFirestore()
                db.collection("app_config").document("security").set(hashMapOf("use_biometric" to enabled)).await()
            } catch (e: Exception) {
                // Abaikan jika Firestore gagal/belum dikonfigurasi
            }
        }
    }
}
