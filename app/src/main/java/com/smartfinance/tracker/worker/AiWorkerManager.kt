package com.smartfinance.tracker.worker

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object AiWorkerManager {

    // 🚨 Pemicu Darurat: Panggil ini saat pengguna memasukkan pengeluaran yang bikin over-budget
    fun triggerBudgetAlert(context: Context, categoryName: String, spent: Double, limit: Double) {
        val percentage = (spent / limit * 100).toInt()
        val data = workDataOf(
            "task_type" to "BUDGET",
            "title" to "Anggaran Menipis: $categoryName",
            "raw_data" to "Kategori '$categoryName' telah terpakai Rp $spent dari limit Rp $limit ($percentage%)."
        )
        enqueueOneTimeWorker(context, data)
    }
    
    // 📅 Pemicu Darurat: Panggil ini oleh Sistem Pemotong Otomatis (Recurring)
    fun triggerRecurringAlert(context: Context, txName: String, amount: Double, isSuccess: Boolean) {
        val status = if (isSuccess) "BERHASIL terpotong otomatis" else "GAGAL terpotong (Saldo tidak cukup)"
        val data = workDataOf(
            "task_type" to "RECURRING",
            "title" to "Tagihan Otomatis: $txName",
            "raw_data" to "Tagihan jatuh tempo '$txName' senilai Rp $amount $status pada hari ini."
        )
        enqueueOneTimeWorker(context, data)
    }

    // 📊 Pemicu Rutin Mingguan
    fun scheduleWeeklyReport(context: Context) {
        val data = workDataOf(
            "task_type" to "WEEKLY_REPORT",
            "title" to "Ringkasan Mingguan & Analisis",
            "raw_data" to "Tolong berikan sapaan motivasi keuangan mingguan dan tips hemat secara umum." 
        )

        // 🔥 TAMBAHKAN setInitialDelay
        val request = PeriodicWorkRequestBuilder<SmartAiWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(7, TimeUnit.DAYS) // Jeda 7 hari dari saat aplikasi pertama diinstall
            .setInputData(data)
            .build()
            
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "AiWeeklyReport",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    // Fungsi internal untuk memicu worker sekali jalan
    private fun enqueueOneTimeWorker(context: Context, data: Data) {
        val request = OneTimeWorkRequestBuilder<SmartAiWorker>()
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}

