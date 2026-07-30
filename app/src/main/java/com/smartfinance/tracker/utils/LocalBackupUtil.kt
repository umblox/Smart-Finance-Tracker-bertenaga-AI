package com.smartfinance.tracker.utils

import android.content.Context
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object LocalBackupUtil {

    // Menyimpan data menggunakan jalur aman (URI) dari File Picker
    fun exportDataToUri(context: Context, uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonString = BackupEngine.exportDbToJson()
                
                // Menulis teks JSON ke dalam file yang dipilih user
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray())
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "✅ Export Sukses! File berhasil disimpan.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "❌ Export Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Membaca data menggunakan jalur aman (URI) dari File Picker
    fun importDataFromUri(context: Context, uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "⏳ Sedang memulihkan data...", Toast.LENGTH_SHORT).show()
                }

                // Membaca isi file JSON langsung dari memori tanpa halangan perizinan
                val jsonString = context.contentResolver.openInputStream(uri)?.bufferedReader().use { it?.readText() }

                if (!jsonString.isNullOrEmpty()) {
                    BackupEngine.importJsonToDbLocal(jsonString)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "✅ Import Sukses! Aplikasi akan dimuat ulang.", Toast.LENGTH_LONG).show()
                        System.exit(0)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "❌ Import Gagal: File kosong atau tidak dapat dibaca.", Toast.LENGTH_LONG).show()
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
