package com.smartfinance.tracker

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.smartfinance.tracker.ui.dashboard.DashboardFragment
import com.smartfinance.tracker.ui.chat.ChatFragment
import com.smartfinance.tracker.ui.debt.AddDebtFragment
import com.smartfinance.tracker.ui.transaction.HistoryTransactionFragment
import com.smartfinance.tracker.ui.settings.SettingsFragment
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

private fun checkBiometric() {
    val prefs = getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
    if (prefs.getBoolean("use_biometric", false)) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { finish() } // Kunci mati kalau gagal
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { /* Akses Diberikan */ }
        })
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Smart Finance Locked")
            .setSubtitle("Gunakan sidik jari untuk membuka")
            .setNegativeButtonText("Batal")
            .build()
        biometricPrompt.authenticate(promptInfo)
    }
}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 🔥 KUNCI UTAMA: Inisialisasi Firebase Dinamis (White-Label Support)
        try {
            val prefs = getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
            val customFirebaseJson = prefs.getString("custom_firebase_json", null)

            // Jika user pernah upload google-services.json dari Settings
            if (customFirebaseJson != null && FirebaseApp.getApps(this).isEmpty()) {
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
            // Jika tidak ada JSON kustom, inisialisasi default dari bawaan aplikasi
            else if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback aman jika parsing JSON kustom gagal
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
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
