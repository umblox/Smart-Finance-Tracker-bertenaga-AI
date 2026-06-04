package com.smartfinance.tracker

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.smartfinance.tracker.ui.dashboard.DashboardFragment
import com.smartfinance.tracker.ui.chat.ChatFragment
import com.smartfinance.tracker.ui.transaction.HistoryTransactionFragment
import com.smartfinance.tracker.ui.settings.SettingsFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        
        // Atur agar teks menu laporan berubah menjadi Transaksi di runtime jika belum diubah di XML
        bottomNavigation.menu.findItem(R.id.menu_report)?.title = "Transaksi"

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.fragment_container, DashboardFragment()).commit()
        }

        bottomNavigation.setOnItemSelectedListener { item ->
            val selectedFragment: Fragment = when (item.itemId) {
                R.id.menu_dashboard -> DashboardFragment()
                R.id.menu_chat -> ChatFragment()
                R.id.menu_report -> HistoryTransactionFragment() // DIUBAH KE TRANSAKSI
                R.id.menu_settings -> SettingsFragment()
                else -> DashboardFragment()
            }
            supportFragmentManager.beginTransaction().replace(R.id.fragment_container, selectedFragment).commit()
            true
        }
    }

    fun navigateToSpecificFragment(fragment: Fragment, activeMenuId: Int? = null) {
        supportFragmentManager.beginTransaction().replace(R.id.fragment_container, fragment).addToBackStack(null).commit()
        activeMenuId?.let { findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId = it }
    }
}
