package com.smartfinance.tracker

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate // ✅ IMPORT BARU UNTUK TEMA
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat // ✅ IMPORT BARU UNTUK BAHASA
import androidx.fragment.app.Fragment
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.smartfinance.tracker.ui.dashboard.DashboardFragment
import com.smartfinance.tracker.ui.chat.ChatFragment
import com.smartfinance.tracker.ui.debt.AddDebtFragment
import com.smartfinance.tracker.ui.transaction.HistoryTransactionFragment
import com.smartfinance.tracker.ui.settings.SettingsFragment
import com.smartfinance.tracker.utils.FirebaseManager

// 🔥 INI JALUR IMPORT YANG BENAR (MENGARAH KE FOLDER UTILS)
import com.smartfinance.tracker.utils.RecurringTxWorker 

class MainActivity : AppCompatActivity() {

    companion object {
        var isFirebaseReady = false
    }

    private fun checkBiometric() {
        val prefs = getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("use_biometric", false)) {
            val executor = ContextCompat.getMainExecutor(this)
            val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { finish() }
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { }
            })
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Smart Finance Locked")
                .setSubtitle("Gunakan sidik jari untuk membuka")
                .setNegativeButtonText("Batal")
                .build()
            biometricPrompt.authenticate(promptInfo)
        }
    }

    fun reinitializeFirebase(): Boolean {
        val prefs = getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        val customFirebaseJson = prefs.getString("custom_firebase_json", null)
        
        if (customFirebaseJson.isNullOrEmpty()) {
            isFirebaseReady = false
            return false
        }

        isFirebaseReady = FirebaseManager.init(this, customFirebaseJson)
        
        if (isFirebaseReady) {
            runFirebaseDependentTasks()
        }
        
        return isFirebaseReady
    }

    private fun runFirebaseDependentTasks() {
        try {
            val prefs = getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
            val db = FirebaseManager.getFirestore()
            
            db.collection("app_config").document("security").get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val cloudBiometric = doc.getBoolean("use_biometric") ?: false
                    prefs.edit().putBoolean("use_biometric", cloudBiometric).apply()
                }
            }
            // 🔥 SISA KODE LAMA YANG BIKIN ERROR SUDAH DIBERSIHKAN
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun showSetupRequiredDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚙️ Persiapan Aplikasi")
            .setMessage("Selamat datang!\n\nUntuk memulai, Anda wajib memasukkan File Database (google-services.json) dan API Key Mesin AI terlebih dahulu.")
            .setCancelable(false)
            .setPositiveButton("Buka Pengaturan") { _, _ ->
                findViewById<BottomNavigationView>(R.id.bottomNavigation).selectedItemId = R.id.menu_settings
            }
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // ==========================================
        // ✅ INJEKSI TEMA DAN BAHASA SEBELUM TAMPILAN DIGAMBAR
        // ==========================================
        val prefs = getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        
        val savedTheme = prefs.getInt("app_theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedTheme)

        val savedLang = prefs.getString("app_language", "id") ?: "id"
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != savedLang) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(savedLang))
        }
        // ==========================================

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ==========================================
        // 🔥 ALARM WORKMANAGER (AKAN BEKERJA OTOMATIS)
        // ==========================================
        val workRequest = PeriodicWorkRequestBuilder<RecurringTxWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "RecurringTransactionWorker",
            ExistingPeriodicWorkPolicy.KEEP, 
            workRequest
        )
        // ==========================================

        checkBiometric()
        
        val hasFirebaseJson = !prefs.getString("custom_firebase_json", "").isNullOrEmpty()
        val isAiConfigured = !prefs.getString("ai_api_key", "").isNullOrEmpty() || !prefs.getString("groq_key_override", "").isNullOrEmpty()

        if (!hasFirebaseJson || !isAiConfigured) {
            showSetupRequiredDialog()
        } else {
            // Inisialisasi Firebase di background, jangan pakai hasilnya untuk blokir UI
            reinitializeFirebase()
        }

        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNavigation.menu.findItem(R.id.menu_report)?.title = "Transaksi"

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, DashboardFragment())
                .commit()
        }

        bottomNavigation.setOnItemSelectedListener { item ->
            val selectedFragment: Fragment = when (item.itemId) {
                R.id.menu_dashboard -> DashboardFragment()
                R.id.menu_chat -> ChatFragment()
                R.id.menu_report -> HistoryTransactionFragment()
                R.id.menu_debt -> AddDebtFragment()
                R.id.menu_settings -> SettingsFragment()
                else -> DashboardFragment()
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, selectedFragment)
                .commit()
            true
        }
    }

    fun navigateToSpecificFragment(fragment: Fragment, activeMenuId: Int? = null) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
        activeMenuId?.let { 
            findViewById<BottomNavigationView>(R.id.bottomNavigation).selectedItemId = it 
        }
    }
}
