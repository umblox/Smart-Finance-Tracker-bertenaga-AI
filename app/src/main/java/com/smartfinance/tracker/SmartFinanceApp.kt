package com.smartfinance.tracker

import android.app.Application
import android.content.Context
import com.smartfinance.tracker.data.local.DatabaseProvider

class SmartFinanceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Inisialisasi Database Lokal Sekali di Awal
        DatabaseProvider.init(this)

        // 🔥 Sistem Perangkap Crash (Menyimpan log error sebelum force close)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val prefs = getSharedPreferences("smart_finance_crash", Context.MODE_PRIVATE)
            prefs.edit().putString("LAST_CRASH", throwable.stackTraceToString()).commit()
            
            // Lanjutkan proses crash bawaan Android
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
