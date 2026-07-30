package com.smartfinance.tracker.utils

import android.content.Context
import android.os.Environment
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object LocalBackupUtil {
    
    // Sesuai dengan nama database Room Anda di AppDatabase.kt
    private const val DB_NAME = "smart_finance_db" 

    fun exportDatabase(context: Context) {
        try {
            val currentDB = context.getDatabasePath(DB_NAME)
            val dbFiles = listOf(currentDB, File("${currentDB.path}-shm"), File("${currentDB.path}-wal"))
            
            val exportDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SmartFinanceBackup")
            if (!exportDir.exists()) exportDir.mkdirs()

            var count = 0
            dbFiles.forEach { file ->
                if (file.exists()) {
                    val backupFile = File(exportDir, file.name)
                    copyFile(file, backupFile)
                    count++
                }
            }
            Toast.makeText(context, "Berhasil export $count file DB ke folder Downloads/SmartFinanceBackup", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Export Gagal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun importDatabase(context: Context) {
        try {
            val importDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SmartFinanceBackup")
            val currentDB = context.getDatabasePath(DB_NAME)
            
            val dbFiles = listOf(currentDB, File("${currentDB.path}-shm"), File("${currentDB.path}-wal"))

            dbFiles.forEach { file ->
                val backupFile = File(importDir, file.name)
                if (backupFile.exists()) {
                    copyFile(backupFile, file)
                }
            }
            Toast.makeText(context, "Import Sukses! Aplikasi akan ditutup paksa. Silakan buka kembali.", Toast.LENGTH_LONG).show()
            System.exit(0) 
        } catch (e: Exception) {
            Toast.makeText(context, "Import Gagal: Pastikan ada file backup di folder Downloads.", Toast.LENGTH_LONG).show()
        }
    }

    private fun copyFile(source: File, dest: File) {
        FileInputStream(source).use { inStream ->
            FileOutputStream(dest).use { outStream ->
                val buffer = ByteArray(1024)
                var length: Int
                while (inStream.read(buffer).also { length = it } > 0) {
                    outStream.write(buffer, 0, length)
                }
            }
        }
    }
}
