package com.smartfinance.tracker

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.widget.Toast
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

    // 🔥 FUNGSI RE-INISIALISASI DINAMIS: Bisa dipanggil kapan saja tanpa restart
    fun reinitializeFirebase(): Boolean {
        return try {
            val prefs = getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
            val customFirebaseJson = prefs.getString("custom_firebase_json", null)
            
            // Hapus instance Firebase yang sedang aktif (bukan yang DEFAULT)
            val existingApps = FirebaseApp.getApps(this)
            for (app in existingApps) {
                if (app.name != FirebaseApp.DEFAULT_APP_NAME) {
                    app.delete()
                }
            }

            if (!customFirebaseJson.isNullOrEmpty()) {
                val jsonObj = JSONObject(customFirebaseJson)
                val projectInfo = jsonObj.getJSONObject("project_info")
                val clientInfo = jsonObj.getJSONArray("client").getJSONObject(0).getJSONObject("client_info")
                val apiKey = jsonObj.getJSONArray("client").getJSONObject(0).getJSONArray("api_key").getJSONObject(0).getString("current_key")
                
                val options = FirebaseOptions.Builder()
                    .setProjectId(projectInfo.getString("project_id"))
                    .setApplicationId(clientInfo.getString("mobilesdk_app_id"))
                    .setApiKey(apiKey)
                    .build()

                FirebaseApp.initializeApp(this, options)
                
                // Refresh data setelah JSON baru masuk
                runFirebaseDependentTasks()
                Toast.makeText(this, "✅ Database berhasil di-load!", Toast.LENGTH_SHORT).show()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun runFirebaseDependentTasks() {
        try {
            val prefs = getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
            val db = FirebaseFirestore.getInstance()
            
            db.collection("app_config").document("security").get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val cloudBiometric = doc.getBoolean("use_biometric") ?: false
                    prefs.edit().putBoolean("use_biometric", cloudBiometric).apply()
                }
            }

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

        checkBiometric()

        val prefs = getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
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
