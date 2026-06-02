package com.smartfinance.tracker

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.databinding.ActivityMainBinding
import com.smartfinance.tracker.ui.debt.AddDebtFragment
import com.smartfinance.tracker.ui.settings.SettingsFragment
import com.smartfinance.tracker.ui.dashboard.DashboardFragment
import com.smartfinance.tracker.ui.chat.ChatFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inisialisasi Database Lokal Room secara pasif
        AppDatabase.getDatabase(this)

        // Set Fragment pertama kali terbuka (Dashboard)
        if (savedInstanceState == null) {
            loadFragment(DashboardFragment())
        }

        // Logika navigasi menu bawah
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_dashboard -> {
                    loadFragment(DashboardFragment())
                    true
                }
                R.id.menu_chat -> {
                    loadFragment(ChatFragment())
                    true
                }
                R.id.menu_debt -> {
                    loadFragment(AddDebtFragment())
                    true
                }
                R.id.menu_settings -> {
                    loadFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
