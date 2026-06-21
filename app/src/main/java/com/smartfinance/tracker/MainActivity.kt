package com.smartfinance.tracker

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
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
// Pastikan import Worker sesuai dengan lokasi file yang lu buat sebelumnya
import com.smartfinance.tracker.worker.RecurringTxWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    // 🔥 GUARD SYSTEM: Digunakan oleh Fragment untuk mengecek apakah database sudah siap
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

    // 🔥 Fungsi inisialisasi yang sekarang tersentralisasi ke FirebaseManager
    fun reinitializeFirebase(): Boolean {
        val prefs = getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        val customFirebaseJson = prefs.getString("custom_firebase_json", null)
        
        if (customFirebaseJson.isNullOrEmpty()) {
            isFirebaseReady = false
            return false
        }

        // Panggil manager untuk mengurus instance-nya
        isFirebaseReady = FirebaseManager.init(this, customFirebaseJson)
        
        if (isFirebaseReady) {
            runFirebaseDependentTasks()
        }
        
        return isFirebaseReady
    }

    private fun runFirebaseDependentTasks() {
        try {
            val prefs = getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
            
            // 🔥 WAJIB MENGGUNAKAN MANAGER, BUKAN GETINSTANCE LAMA
            val db = FirebaseManager.getFirestore()
            
            db.collection("app_config").document("security").get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val cloudBiometric = doc.getBoolean("use_biometric") ?: false
                    prefs.edit().putBoolean("use_biometric", cloudBiometric).apply()
                }
            }

            // Ini tetap dibiarkan sebagai "Instant Trigger" saat aplikasi baru dibuka
            CoroutineScope(Dispatchers.IO).launch {
                com.smartfinance.tracker.utils.RecurringTxWorker.checkAndExecuteDueTransactions()
            }
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
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ==========================================
        // 🔥 ALARM WORKER DIAKTIFKAN DI SINI
        // ==========================================
        val workRequest = PeriodicWorkRequestBuilder<RecurringTxWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "RecurringTransactionWorker",
            ExistingPeriodicWorkPolicy.KEEP, 
            workRequest
        )
        // ==========================================

        checkBiometric()

        val prefs = getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        
        // Inisialisasi Firebase
        val isFirebaseConfigured = reinitializeFirebase()
        val isAiConfigured = !prefs.getString("ai_api_key", "").isNullOrEmpty() || !prefs.getString("groq_key_override", "").isNullOrEmpty()

        if (!isFirebaseConfigured || !isAiConfigured) {
            showSetupRequiredDialog()
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
