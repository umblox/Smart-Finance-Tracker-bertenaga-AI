package com.smartfinance.tracker

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.smartfinance.tracker.ui.dashboard.DashboardFragment
import com.smartfinance.tracker.ui.chat.ChatFragment
import com.smartfinance.tracker.ui.debt.AddDebtFragment
import com.smartfinance.tracker.ui.settings.SettingsFragment
import com.smartfinance.tracker.ui.transaction.HistoryTransactionFragment // TAB BARU PENAMPUNG MUTASI KAS PER HARI
import com.smartfinance.tracker.ui.report.ReportFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        if (savedInstanceState == null) {
            loadFragment(DashboardFragment())
        }

        bottomNav.setOnItemSelectedListener { item ->
            val targetFragment: Fragment = when (item.itemId) {
                R.id.menu_dashboard -> DashboardFragment()
                R.id.menu_chat -> ChatFragment()
                // MENGUBAH FUNGSI KLIK MENJADI HALAMAN DAFTAR TRANSAKSI
                R.id.menu_report -> HistoryTransactionFragment() 
                R.id.menu_debt -> AddDebtFragment()
                R.id.menu_settings -> SettingsFragment()
                else -> DashboardFragment()
            }
            loadFragment(targetFragment)
            true
        }
    }

    // FUNGSI UTAMA AKSES SHORTCUT INTERNAL UNTUK MELOMPAT ANTAR LAYAR FRAGMENT
    fun navigateToSpecificFragment(fragment: Fragment, activeMenuId: Int? = null) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
            
        if (activeMenuId != null) {
            findViewById<BottomNavigationView>(R.id.bottomNavigation).selectedItemId = activeMenuId
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
