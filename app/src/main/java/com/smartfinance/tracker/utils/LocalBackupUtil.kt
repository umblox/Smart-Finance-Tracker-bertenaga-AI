package com.smartfinance.tracker.utils

import android.content.Context
import android.os.Environment
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object LocalBackupUtil {

    fun exportDatabase(context: Context) {
        // Karena BackupEngine menggunakan suspend function, kita jalankan di Coroutine
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Ekstrak seluruh isi DB menjadi satu teks JSON menggunakan mesin sinkronisasi Cloud
                val jsonString = BackupEngine.exportDbToJson()
                
                // 2. Siapkan folder tujuan
                val exportDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SmartFinanceBackup")
                if (!exportDir.exists()) exportDir.mkdirs()

                // 3. Simpan sebagai file teks murni (.json) untuk menghindari blokir Scoped Storage Android
                val backupFile = File(exportDir, "smart_finance_backup.json")
                backupFile.writeText(jsonString)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "✅ Export Sukses!\nFile: Downloads/SmartFinanceBackup/smart_finance_backup.json", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "❌ Export Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun importDatabase(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val importDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SmartFinanceBackup")
                // Kita cari file JSON-nya, bukan file .db mentah
                val backupFile = File(importDir, "smart_finance_backup.json")

                if (backupFile.exists()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "⏳ Sedang memulihkan data...", Toast.LENGTH_SHORT).show()
                    }

                    // 1. Baca isi file JSON
                    val jsonString = backupFile.readText()
                    
                    // 2. Suntikkan ke dalam Room Database dengan aman
                    val success = BackupEngine.importJsonToDb(jsonString)

                    withContext(Dispatchers.Main) {
                        if (success) {
                            Toast.makeText(context, "✅ Import Sukses! Aplikasi akan dimuat ulang.", Toast.LENGTH_LONG).show()
                            // Tutup paksa agar UI memuat data terbaru dari Room
                            System.exit(0)
                        } else {
                            Toast.makeText(context, "❌ Import Gagal: Data JSON rusak atau tidak valid.", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "❌ Gagal: File 'smart_finance_backup.json' tidak ditemukan di folder SmartFinanceBackup!", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "❌ Import Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
