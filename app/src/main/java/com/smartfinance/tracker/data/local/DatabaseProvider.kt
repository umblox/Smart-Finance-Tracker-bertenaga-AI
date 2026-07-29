package com.smartfinance.tracker.data.local

import android.content.Context

object DatabaseProvider {
    lateinit var db: AppDatabase
    
    fun init(context: Context) {
        db = AppDatabase.getDatabase(context)
    }
}

