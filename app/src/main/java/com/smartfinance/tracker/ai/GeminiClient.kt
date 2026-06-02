package com.smartfinance.tracker.ai

import android.content.Context
import com.smartfinance.tracker.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class GeminiClient(private val context: Context, private val assistant: FinancialAssistant) {

    suspend fun sendMessageToAI(userMessage: String): String = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "") ?: ""

        if (apiKey.isEmpty()) {
            return@withContext "⚠️ API Key Groq belum diatur di Pengaturan. Mengalihkan ke sistem lokal...\n\n" + 
                    assistant.processLocalFallback(userMessage, "API Key Kosong")
        }

        // Ambil riwayat dari SQLite untuk bekal otak Groq
        val db = AppDatabase.getDatabase(context)
        val transactions = db.transactionDao().getAllTransactions().first()
        val txHistory = StringBuilder()
        transactions.take(10).forEach { 
            txHistory.append("- ${it.type}: ${it.categoryName} (${it.note}) Rp ${it.amount}\n")
        }

        val systemPrompt = """
            Anda adalah Asisten Keuangan Pribadi pintar di aplikasi Smart Finance Tracker.
            Berikut data transaksi user saat ini di database SQLite HP:
            $txHistory
            
            Tugas Anda:
            1. Jawab pertanyaan keuangan, analisis pengeluaran, atau berikan saran hemat dengan ramah.
            2. Jika user ingin MEMBUAT KATEGORI (misal: "buat kategori kopi"), respon Anda WAJIB mengandung kata kunci khusus: "CMD_CREATE_CATEGORY:NamaKategori:Tipe" (Tipe: INCOME atau EXPENSE).
            
            Gunakan Bahasa Indonesia yang baik dan santun.
        """.trimIndent()

        // 🚀 UPAYAKAN ONLINE GROQ ENGINE DULUAN
        try {
            val url = URL("https://api.groq.com/openai/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.connectTimeout = 6000 // Terapkan timeout agar tidak hang lama
            conn.readTimeout = 6000
            conn.doOutput = true

            val jsonBody = JSONObject().apply {
                put("model", "llama3-8b-8192")
                val messagesArray = org.json.JSONArray().apply {
                    put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                    put(JSONObject().apply { put("role", "user"); put("content", userMessage) })
                }
                put("messages", messagesArray)
                put("temperature", 0.6)
            }

            conn.outputStream.use { os -> os.write(jsonBody.toString().toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val responseStr = reader.readText()
                val jsonResponse = JSONObject(responseStr)
                val aiText = jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                
                // Cek apakah Groq memberikan instruksi pembuatan kategori sistem
                if (aiText.contains("CMD_CREATE_CATEGORY")) {
                    return@withContext assistant.executeInterceptorCommand(aiText)
                }
                
                return@withContext aiText
            } else {
                val errorReader = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream))
                val errorLog = errorReader.readText()
                val shortError = "Http Code ${conn.responseCode}"
                
                // Groq gagal/limit -> Lempar ke AI Lokal dengan pemberitahuan
                return@withContext "⚠️ **Groq API gagal ($shortError)**. Mengalihkan otomatis ke AI Lokal...\n\n" + 
                        assistant.processLocalFallback(userMessage, errorLog)
            }
        } catch (e: Exception) {
            // Koneksi putus/timeout -> Lempar ke AI Lokal dengan pemberitahuan
            return@withContext "⚠️ **Groq Terputus (${e.message})**. Mengalihkan otomatis ke AI Lokal...\n\n" + 
                    assistant.processLocalFallback(userMessage, e.message ?: "Unknown Connection Error")
        }
    }
}
