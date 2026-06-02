package com.smartfinance.tracker

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.databinding.ActivityMainBinding
import com.smartfinance.tracker.ui.debt.AddDebtFragment
import com.smartfinance.tracker.ui.settings.SettingsFragment // Representasi dari fragment pengaturan XML fase lalu

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inisialisasi Database Lokal Room secara pasif di awal
        AppDatabase.getDatabase(this)

        // Set Fragment default saat aplikasi pertama kali terbuka (Dashboard)
        if (savedInstanceState == null) {
            loadFragment(DummyDashboardFragment()) // Sementara diarahkan ke penampung fragmen dasar
        }

        // Logika klik navigasi bawah untuk berpindah menu
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_dashboard -> {
                    loadFragment(DummyDashboardFragment())
                    true
                }
                R.id.menu_chat -> {
                    loadFragment(DummyChatFragment())
                    true
                }
                R.id.menu_debt -> {
                    loadFragment(AddDebtFragment())
                    true
                }
                R.id.menu_settings -> {
                    // Mengarah ke layout pengaturan API Gemini yang dibuat pada fase 3
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

// ==========================================
// PENAMPUNG FRAGMENT DUMMY UNTUK COMPILING
// Agar build gradle sukses tanpa error class missing
// ==========================================
class DummyDashboardFragment : Fragment(R.layout.fragment_dashboard)
class DummyChatFragment : Fragment(R.layout.fragment_chat)

