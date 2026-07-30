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

    // 🔥 NAMA FILE DISAMAKAN PERSIS DENGAN GOOGLE DRIVE
    private const val BACKUP_FILE_NAME = "SmartFinance_Backup.json"

    fun exportDatabase(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonString = BackupEngine.exportDbToJson()
                
                val exportDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SmartFinanceBackup")
                if (!exportDir.exists()) exportDir.mkdirs()

                val backupFile = File(exportDir, BACKUP_FILE_NAME)
                backupFile.writeText(jsonString)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "✅ Export Sukses!\nFile: Downloads/SmartFinanceBackup/$BACKUP_FILE_NAME", Toast.LENGTH_LONG).show()
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
                val backupFile = File(importDir, BACKUP_FILE_NAME)

                if (backupFile.exists()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "⏳ Sedang memulihkan data...", Toast.LENGTH_SHORT).show()
                    }

                    // 1. Baca isi file JSON
                    val jsonString = backupFile.readText()
                    
                    // 2. Suntikkan ke DB (Jika ada error parsing/format, akan meledak di sini dan ditangkap oleh catch)
                    BackupEngine.importJsonToDbLocal(jsonString)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "✅ Import Sukses! Aplikasi akan dimuat ulang.", Toast.LENGTH_LONG).show()
                        System.exit(0)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "❌ Gagal: File '$BACKUP_FILE_NAME' tidak ditemukan di folder SmartFinanceBackup!", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // 🔥 Jika terjadi Permission Denied atau Gagal Parsing, kita akan bisa membaca alasannya di Toast ini!
                    Toast.makeText(context, "❌ Import Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
