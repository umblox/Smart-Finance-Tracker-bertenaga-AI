package com.smartfinance.tracker

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.smartfinance.tracker.ui.dashboard.DashboardFragment
import com.smartfinance.tracker.ui.chat.ChatFragment
import com.smartfinance.tracker.ui.debt.AddDebtFragment
import com.smartfinance.tracker.ui.transaction.HistoryTransactionFragment
import com.smartfinance.tracker.ui.settings.SettingsFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private fun checkBiometric() {
        val prefs = getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("use_biometric", false)) {
            val executor = ContextCompat.getMainExecutor(this)
            val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { 
                    finish() // Kunci mati kalau user batalin atau salah jari
                }
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { 
                    // Akses Diberikan, tidak ada aksi (biarkan aplikasi terbuka)
                }
            })
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Smart Finance Locked")
                .setSubtitle("Gunakan sidik jari untuk membuka")
                .setNegativeButtonText("Batal")
                .build()
            biometricPrompt.authenticate(promptInfo)
        }
    }

    // 🔥 FUNGSI BARU: Inisialisasi Firebase MURNI dari data custom (BYOK)
    private fun initializeFirebaseDynamic(): Boolean {
        try {
            // Bersihkan instance lama agar JSON baru bisa masuk tanpa konflik
            val existingApps = FirebaseApp.getApps(this)
            for (app in existingApps) {
                if (app.name != FirebaseApp.DEFAULT_APP_NAME) {
                    app.delete()
                }
            }

            val prefs = getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
            val customFirebaseJson = prefs.getString("custom_firebase_json", null)

            if (!customFirebaseJson.isNullOrEmpty()) {
                if (FirebaseApp.getApps(this).isEmpty()) {
                    val jsonObj = JSONObject(customFirebaseJson)
                    val projectInfo = jsonObj.getJSONObject("project_info")
                    val clientInfo = jsonObj.getJSONArray("client").getJSONObject(0).getJSONObject("client_info")
                    val apiKey = jsonObj.getJSONArray("client").getJSONObject(0).getJSONArray("api_key").getJSONObject(0).getString("current_key")
                    
                    val projectId = projectInfo.getString("project_id")
                    val appId = clientInfo.getString("mobilesdk_app_id")
                    
                    val options = FirebaseOptions.Builder()
                        .setProjectId(projectId)
                        .setApplicationId(appId)
                        .setApiKey(apiKey)
                        .build()

                    FirebaseApp.initializeApp(this, options)
                }
                return true // Firebase sukses diinisialisasi dari setting lokal
            }
            return false // Tidak ada konfigurasi Firebase lokal
        } catch (e: Exception) {
            e.printStackTrace()
            return false // JSON rusak atau tidak valid
        }
    }

    // 🔥 FUNGSI BARU: Menjalankan fitur yang butuh koneksi Cloud HANYA JIKA Firebase sudah siap
    private fun runFirebaseDependentTasks() {
        try {
            val prefs = getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
            val db = FirebaseFirestore.getInstance()
            
            // Sinkronisasi Status Biometrik dari Cloud (Untuk Re-install)
            db.collection("app_config").document("security").get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val cloudBiometric = doc.getBoolean("use_biometric") ?: false
                    prefs.edit().putBoolean("use_biometric", cloudBiometric).apply()
                }
            }

            // Jalankan Mesin Pengecek Transaksi Berkala di Background
            CoroutineScope(Dispatchers.IO).launch {
                com.smartfinance.tracker.utils.RecurringTxWorker.checkAndExecuteDueTransactions()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 🔥 FUNGSI BARU: Paksa user pindah ke Setting jika belum BYOK
    private fun showSetupRequiredDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚙️ Persiapan Aplikasi")
            .setMessage("Selamat datang!\n\nUntuk memulai, Anda wajib memasukkan File Database (google-services.json) dan API Key Mesin AI terlebih dahulu.\n\nKlik tombol di bawah untuk menuju ke Pengaturan.")
            .setCancelable(false) // Tidak bisa di-cancel dengan tombol back atau tap di luar area
            .setPositiveButton("Buka Pengaturan") { _, _ ->
                // Arahkan otomatis ke Menu Settings
                findViewById<BottomNavigationView>(R.id.bottomNavigation).selectedItemId = R.id.menu_settings
            }
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Panggil pengunci Biometrik sebelum data lain diload
        checkBiometric()

        val prefs = getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        val isFirebaseConfigured = initializeFirebaseDynamic()
        
        // Kita ngecek API key lama (groq) dan key baru (ai_api_key) sebagai transisi
        val isAiConfigured = !prefs.getString("ai_api_key", "").isNullOrEmpty() || !prefs.getString("groq_key_override", "").isNullOrEmpty()

        if (!isFirebaseConfigured || !isAiConfigured) {
            // Jika salah satu dari JSON atau API Key belum diisi, panggil popup pencegat
            showSetupRequiredDialog()
        } else {
            // Jika sudah terisi semua, jalankan proses background Firestore
            runFirebaseDependentTasks()
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
