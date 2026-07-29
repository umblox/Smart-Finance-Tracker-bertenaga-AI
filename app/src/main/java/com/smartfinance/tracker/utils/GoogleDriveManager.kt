package com.smartfinance.tracker.utils

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object GoogleDriveManager {

    private suspend fun getToken(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context)?.account ?: return@withContext null
            // Meminta Token Khusus Akses Tulis/Baca File Drive
            GoogleAuthUtil.getToken(context, account, "oauth2:https://www.googleapis.com/auth/drive.file")
        } catch (e: Exception) { null }
    }

    suspend fun checkBackupFileId(context: Context): String? = withContext(Dispatchers.IO) {
        val token = getToken(context) ?: return@withContext null
        try {
            val query = URLEncoder.encode("name='SmartFinance_Backup.json' and trashed=false", "UTF-8")
            val url = URL("https://www.googleapis.com/drive/v3/files?q=$query&spaces=drive")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $token")

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val files = JSONObject(response).getJSONArray("files")
                if (files.length() > 0) return@withContext files.getJSONObject(0).getString("id")
            }
        } catch (e: Exception) { e.printStackTrace() }
        null
    }

    suspend fun uploadBackup(context: Context, jsonContent: String): Boolean = withContext(Dispatchers.IO) {
        val token = getToken(context) ?: return@withContext false
        val existingFileId = checkBackupFileId(context)
        
        try {
            if (existingFileId != null) {
                // Update File Jika Sudah Ada
                val url = URL("https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=media")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("X-HTTP-Method-Override", "PATCH") // Workaround HTTP API
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Content-Type", "application/json")
                
                conn.outputStream.write(jsonContent.toByteArray())
                conn.outputStream.flush()
                return@withContext conn.responseCode == 200
            } else {
                // Buat File Baru (Multipart Upload)
                val boundary = "Boundary-${System.currentTimeMillis()}"
                val url = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")

                val crlf = "\r\n"
                val out = conn.outputStream
                
                out.write(("--$boundary$crlf").toByteArray())
                out.write(("Content-Type: application/json; charset=UTF-8$crlf$crlf").toByteArray())
                out.write(("{ \"name\": \"SmartFinance_Backup.json\" }$crlf").toByteArray())
                out.write(("--$boundary$crlf").toByteArray())
                out.write(("Content-Type: application/json$crlf$crlf").toByteArray())
                out.write((jsonContent + crlf).toByteArray())
                out.write(("--$boundary--$crlf").toByteArray())
                out.flush()
                
                return@withContext conn.responseCode == 200
            }
        } catch (e: Exception) { return@withContext false }
    }

    suspend fun downloadBackup(context: Context): String? = withContext(Dispatchers.IO) {
        val token = getToken(context) ?: return@withContext null
        val fileId = checkBackupFileId(context) ?: return@withContext null
        
        try {
            val url = URL("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $token")
            
            if (conn.responseCode == 200) return@withContext conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) { e.printStackTrace() }
        null
    }
}
