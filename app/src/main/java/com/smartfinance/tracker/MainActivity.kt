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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    // Menggunakan delegated lazy agar objek binding dibuat dengan aman saat siklus siap
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Memastikan menggunakan root dari objek binding lazy
        setContentView(binding.root)

        // AMAN: Pindahkan inisialisasi database ke Background Thread (IO) agar tidak memicu mental/crash
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(this@MainActivity)
        }

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
