package com.smartfinance.tracker

import android.app.Application
import android.content.Context
import com.smartfinance.tracker.data.local.DatabaseProvider

class CrashHandlerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Inisialisasi Database Room di sini agar aman
        DatabaseProvider.init(this)

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Tangkap error dan simpan ke SharedPreferences
            val prefs = getSharedPreferences("smart_finance_crash", Context.MODE_PRIVATE)
            prefs.edit().putString("LAST_CRASH", throwable.stackTraceToString()).apply()
            
            // Biarkan aplikasi mati secara natural
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
