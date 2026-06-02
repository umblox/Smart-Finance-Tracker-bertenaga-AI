package com.smartfinance.tracker

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. CARI NAV HOST SECARA MANUAL MENGGUNAKAN LOOPING TYPE
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as? NavHostFragment
            ?: supportFragmentManager.fragments.firstOrNull { it is NavHostFragment } as? NavHostFragment

        if (navHostFragment != null) {
            val navController = navHostFragment.navController

            // 2. CARI BOTTOM VIEW SECARA MANUAL DENGAN ID ATAU METODE LOOKUP TYPE
            val bottomNav = findViewById<BottomNavigationView>(R.id.nav_view) 
                ?: findViewById<BottomNavigationView>(resources.getIdentifier("navView", "id", packageName))
                ?: findViewById<BottomNavigationView>(resources.getIdentifier("bottom_nav", "id", packageName))
                ?: findViewById<ViewGroup>(android.R.id.content).getChildAt(0).let { findBottomNavInView(it) }

            // 3. DAFTARKAN SEMUA 5 ID MENU UTAMA AGAR TIDAK KEMBALI KE DASHBOARD
            val appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.menu_dashboard, 
                    R.id.menu_chat, 
                    R.id.menu_report, 
                    R.id.menu_debt, 
                    R.id.menu_settings
                )
            )

            setupActionBarWithNavController(navController, appBarConfiguration)
            bottomNav?.setupWithNavController(navController)
        }
    }

    // Fungsi rekursif cadangan untuk mencari komponen BottomNav di layar jika semua ID meleset
    private fun findBottomNavInView(view: android.view.View): BottomNavigationView? {
        if (view is BottomNavigationView) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                val result = findBottomNavInView(child)
                if (result != null) return result
            }
        }
        return null
    }
}
