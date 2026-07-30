package com.smartfinance.tracker

import android.app.Application
import android.content.Context

class CrashHandlerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Tangkap error dan simpan ke SharedPreferences sebelum aplikasi mati
            val prefs = getSharedPreferences("smart_finance_crash", Context.MODE_PRIVATE)
            prefs.edit().putString("LAST_CRASH", throwable.stackTraceToString()).commit()
            
            // Biarkan aplikasi mati secara natural
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}

