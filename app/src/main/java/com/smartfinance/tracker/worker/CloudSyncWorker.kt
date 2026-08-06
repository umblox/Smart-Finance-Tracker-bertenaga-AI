package com.smartfinance.tracker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.smartfinance.tracker.R
import com.smartfinance.tracker.utils.BackupEngine
import com.smartfinance.tracker.utils.GoogleDriveManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CloudSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (GoogleSignIn.getLastSignedInAccount(applicationContext) == null) {
            return Result.success()
        }

        return try {
            val jsonContent = BackupEngine.exportDbToJson()
            val success = GoogleDriveManager.uploadBackup(applicationContext, jsonContent)
            if (success) {
                // 🔥 REKAM JEJAK: AUTO SYNC BERHASIL
                val prefs = applicationContext.getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
                
                // 🔥 FIX: Menggunakan Locale.getDefault() agar bulan dinamis (Agt/Aug)
                val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                val timeStr = sdf.format(Date())
                
                // 🔥 FIX: Menyematkan string dinamis dwibahasa
                val syncLabel = applicationContext.getString(R.string.sync_auto_label, timeStr)
                prefs.edit().putString("last_sync_time", syncLabel).apply()
                
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
