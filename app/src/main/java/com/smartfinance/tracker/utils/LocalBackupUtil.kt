package com.smartfinance.tracker.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.smartfinance.tracker.R
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
                    Toast.makeText(context, context.getString(R.string.backup_export_success), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.backup_export_failed, e.localizedMessage), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Membaca data menggunakan jalur aman (URI) dari File Picker
    fun importDataFromUri(context: Context, uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.backup_import_loading), Toast.LENGTH_SHORT).show()
                }

                // Membaca isi file JSON langsung dari memori tanpa halangan perizinan
                val jsonString = context.contentResolver.openInputStream(uri)?.bufferedReader().use { it?.readText() }

                if (!jsonString.isNullOrEmpty()) {
                    BackupEngine.importJsonToDbLocal(jsonString)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.backup_import_success), Toast.LENGTH_LONG).show()
                        
                        // 🔥 FIX: Logika Restart Aplikasi Yang Sesungguhnya
                        val packageManager = context.packageManager
                        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
                        if (intent != null) {
                            // Bersihkan semua tumpukan layar (stack) yang lama, buat sesi baru
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                        
                        // Matikan proses lama agar database di-refresh sepenuhnya dari memori
                        Runtime.getRuntime().exit(0)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.backup_import_empty), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.backup_import_error, e.localizedMessage), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
