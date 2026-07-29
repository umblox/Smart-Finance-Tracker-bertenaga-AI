package com.smartfinance.tracker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.smartfinance.tracker.utils.BackupEngine
import com.smartfinance.tracker.utils.GoogleDriveManager

class CloudSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Jika belum login Google, tak perlu error, cukup lewati task ini
        val account = GoogleSignIn.getLastSignedInAccount(applicationContext) ?: return Result.success()

        return try {
            val jsonContent = BackupEngine.exportDbToJson()
            val success = GoogleDriveManager.uploadBackup(applicationContext, jsonContent)
            if (success) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
