package com.smartfinance.tracker

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate 
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat 
import androidx.fragment.app.Fragment
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.smartfinance.tracker.ui.dashboard.DashboardFragment
import com.smartfinance.tracker.ui.chat.ChatFragment
import com.smartfinance.tracker.ui.debt.AddDebtFragment
import com.smartfinance.tracker.ui.transaction.HistoryTransactionFragment
import com.smartfinance.tracker.ui.settings.SettingsFragment
import com.smartfinance.tracker.ui.transaction.TransactionManualDialog
import com.smartfinance.tracker.utils.RecurringTxWorker 
import com.smartfinance.tracker.worker.AiWorkerManager 
import com.smartfinance.tracker.worker.CloudSyncWorker
// 🔥 IMPORT BARU UNTUK SCRIPT MIGRASI
import com.smartfinance.tracker.data.local.DatabaseProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

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

    private fun showSetupRequiredDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("⚙️ Persiapan Aplikasi")
            .setMessage("Selamat datang!\n\nUntuk memulai, Anda wajib memasukkan API Key Mesin AI terlebih dahulu.")
            .setCancelable(false)
            .setPositiveButton("Buka Pengaturan") { _, _ ->
                findViewById<BottomNavigationView>(R.id.bottomNavigation).selectedItemId = R.id.menu_settings
            }
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        
        val savedTheme = prefs.getInt("app_theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedTheme)

        val savedLang = prefs.getString("app_language", "id") ?: "id"
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != savedLang) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(savedLang))
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 🔥 SCRIPT AUTO-MIGRATION IKON DATABASE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = DatabaseProvider.db
                val dao = db.categoryDao()
                val cats = dao.getAllSync()
                
                // Jika ikon terkunci masih menggunakan ic_wallet atau salah, update otomatis ke ikon baru
                cats.find { it.id == 1L && it.iconName != "ic_salary" }?,let {dao.insert(it.copy(iconName = "ic_salary")) }
                cats.find { it.id == 101L && it.iconName != "ic_debt" }?.let { dao.insert(it.copy(iconName = "ic_debt")) }
                cats.find { it.id == 102L && it.iconName != "ic_debt_pay" }?.let { dao.insert(it.copy(iconName = "ic_debt_pay")) }
                cats.find { it.id == 103L && it.iconName != "ic_receivable_collect" }?.let { dao.insert(it.copy(iconName = "ic_receivable_collect")) }
                cats.find { it.id == 104L && it.iconName != "ic_receivable" }?.let { dao.insert(it.copy(iconName = "ic_receivable")) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val workRequest = PeriodicWorkRequestBuilder<RecurringTxWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("RecurringTransactionWorker", ExistingPeriodicWorkPolicy.KEEP, workRequest)
        AiWorkerManager.scheduleWeeklyReport(this)
        
        val syncRequest = PeriodicWorkRequestBuilder<CloudSyncWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("CloudSyncWorker", ExistingPeriodicWorkPolicy.KEEP, syncRequest)

        checkBiometric()
        
        val isAiConfigured = !prefs.getString("ai_api_key", "").isNullOrEmpty() || !prefs.getString("groq_key_override", "").isNullOrEmpty()
        if (!isAiConfigured) {
            showSetupRequiredDialog()
        }

        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, DashboardFragment())
                .commit()
        }

        bottomNavigation.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.menu_manual_add) {
                TransactionManualDialog {
                }.show(supportFragmentManager, "TransactionManualDialog")
                return@setOnItemSelectedListener false 
            }

            val selectedFragment: Fragment = when (item.itemId) {
                R.id.menu_dashboard -> DashboardFragment()
                R.id.menu_debt -> AddDebtFragment()
                R.id.menu_chat -> ChatFragment()
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
            findViewById<BottomNavigationView>(R.id.bottomNavigation).menu.findItem(it)?.isChecked = true
        }
    }
}
