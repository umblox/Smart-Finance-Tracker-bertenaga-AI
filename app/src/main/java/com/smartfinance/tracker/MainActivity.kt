package com.smartfinance.tracker

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.smartfinance.tracker.databinding.ActivityMainBinding
import com.smartfinance.tracker.utils.FirebaseManager

class MainActivity : AppCompatActivity() {

    private lateinit binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Terapkan Tema dan Bahasa dari Shared Preferences sebelum menggambar layar
        val prefs = getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        
        val savedTheme = prefs.getInt("app_theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedTheme)

        val savedLang = prefs.getString("app_language", "id") ?: "id"
        // Hanya panggil setApplicationLocales jika locale saat ini berbeda agar tidak loop/kedip
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != savedLang) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(savedLang))
        }

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inisialisasi Firebase berdasarkan kunci JSON rahasia user
        reinitializeFirebase()

        // Setup Bottom Navigation Murni Tanpa SetupActionBar
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNavigation.setupWithNavController(navController)
    }

    fun reinitializeFirebase() {
        val prefs = getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        val customJson = prefs.getString("custom_firebase_json", null)
        
        if (customJson != null) {
            FirebaseManager.initializeWithCustomJson(this, customJson)
        } else {
            FirebaseManager.initializeDefault(this)
        }
    }
}
