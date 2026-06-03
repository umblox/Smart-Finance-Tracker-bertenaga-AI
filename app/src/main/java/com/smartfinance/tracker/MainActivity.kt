package com.smartfinance.tracker

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.smartfinance.tracker.ui.dashboard.DashboardFragment
import com.smartfinance.tracker.ui.chat.ChatFragment
import com.smartfinance.tracker.ui.debt.AddDebtFragment
import com.smartfinance.tracker.ui.settings.SettingsFragment
import com.smartfinance.tracker.ui.report.ReportFragment // ALAMAT IMPORT BARU DISINKRONKAN

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
                R.id.menu_report -> ReportFragment()
                R.id.menu_debt -> AddDebtFragment()
                R.id.menu_settings -> SettingsFragment()
                else -> DashboardFragment()
            }
            loadFragment(targetFragment)
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
