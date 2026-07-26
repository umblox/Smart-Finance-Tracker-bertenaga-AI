package com.smartfinance.tracker.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.smartfinance.tracker.MainActivity
import com.smartfinance.tracker.R
import com.smartfinance.tracker.ai.AIClient
import com.smartfinance.tracker.data.model.AiNotification
import com.smartfinance.tracker.data.repository.AiNotificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmartAiWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val taskType = inputData.getString("task_type") ?: "INFO"
        val rawData = inputData.getString("raw_data") ?: ""
        val titlePrompt = inputData.getString("title") ?: "Pesan dari Asisten AI"

        try {
            // 1. Susun Prompt Cerdas untuk AI (Menghindari Token Limit & Context Lenght)
            // AI hanya diberikan ringkasan (JSON ringan), bukan ribuan transaksi mentah.
            val prompt = """
                Kamu adalah asisten keuangan pribadi yang ramah, cerdas, dan empatik.
                Tugasmu: Berikan nasihat, peringatan, atau apresiasi berdasarkan data ringkas berikut.
                Tipe Laporan: $taskType
                Data Mentah: $rawData
                
                Aturan Output:
                - Tulis MAKSIMAL 3 kalimat pendek saja.
                - Gunakan bahasa Indonesia santai tapi profesional (seperti sahabat yang melek finansial).
                - Berikan satu langkah konkrit atau solusi.
                - Jangan memakai sapaan basa-basi (seperti "Halo/Hai"), langsung to the point.
            """.trimIndent()

            // 2. Inisialisasi Otak AI Client sesuai dengan struktur aplikasi Anda
            val assistant = com.smartfinance.tracker.ai.FinancialAssistant(context)
            val aiClient = com.smartfinance.tracker.ai.AIClient(context, assistant)

             // Panggil fungsi dari AiClient.kt
            val aiResponse = aiClient.sendMessageToAI(prompt)
            
            // 3. Simpan Jawaban AI ke Firestore (Agar masuk ke Kotak Pesan)
            val repo = AiNotificationRepository()
            val notif = AiNotification(
                title = titlePrompt,
                message = aiResponse,
                timestamp = System.currentTimeMillis(),
                type = taskType,
                isRead = false // Default: Belum dibaca (akan memicu titik merah)
            )
            repo.saveNotification(notif)

            // 4. Tampilkan Notifikasi Sistem Android (Pop-up di layar HP)
            showAndroidNotification(titlePrompt, aiResponse)

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            // Jika gagal (misal koneksi internet putus), WorkManager akan mengulanginya nanti
            Result.retry() 
        }
    }

    private fun showAndroidNotification(title: String, message: String) {
        val channelId = "ai_advisor_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Wajib untuk Android 8 (Oreo) ke atas
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Asisten AI Smart Finance", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        // Jika notifikasi diklik, buka aplikasi
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Bisa diganti logo app Anda
            .setContentTitle("🤖 $title")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message)) // Teks panjang bisa diexpand
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}

