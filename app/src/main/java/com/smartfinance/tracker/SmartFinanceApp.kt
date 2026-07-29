package com.smartfinance.tracker

import android.app.Application
import com.smartfinance.tracker.data.local.DatabaseProvider

class SmartFinanceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Inisialisasi Database Lokal Sekali di Awal
        DatabaseProvider.init(this)
    }
}

