package com.smartfinance.tracker

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.smartfinance.tracker.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navController = findNavController(R.id.nav_host_fragment_activity_main)

        // DAFTARKAN SEMUA 5 ID MENU UTAMA BIAR TIDAK BERKEDIP ATAU KEMBALI KE DASHBOARD
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.menu_dashboard, 
                R.id.menu_chat, 
                R.id.menu_report, // WAJIB ADA DISINI
                R.id.menu_debt, 
                R.id.menu_settings
            )
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)
    }
}
