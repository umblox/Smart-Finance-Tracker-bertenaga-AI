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
        // 1. Cek interceptor laporan atau manajemen kategori lokal terlebih dahulu
        val localResponse = assistant.processNaturalLanguage(userMessage)
        if (!localResponse.contains("Format kurang spesifik")) {
            return@withContext localResponse
        }

        // 2. Ambil API Key dari menu Pengaturan (Nanti masukkan API Key Groq kamu di sini)
        val prefs = context.getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "") ?: ""
        if (apiKey.isEmpty()) {
            return@withContext "API Key belum diatur di menu Pengaturan. Silakan masukkan API Key Groq Anda terlebih dahulu."
        }

        // 3. Ambil data transaksi nyata dari SQLite untuk konteks otak AI
        val db = AppDatabase.getDatabase(context)
        val transactions = db.transactionDao().getAllTransactions().first()
        val txHistory = StringBuilder()
        transactions.take(15).forEach { 
            txHistory.append("- ${it.type}: ${it.categoryName} (${it.note}) sebesar Rp ${it.amount}\n")
        }

        val systemPrompt = """
            Anda adalah Asisten Keuangan Pribadi pintar di aplikasi Smart Finance Tracker.
            Berikut adalah data transaksi nyata pengguna saat ini di dalam database local HP:
            $txHistory
            
            Tugas Anda adalah menjawab pertanyaan keuangan, menganalisis pengeluaran mereka, atau memberikan rekomendasi penghematan berdasarkan data transaksi di atas. Jawablah dengan ramah, santun, dan gunakan format Bahasa Indonesia yang rapi.
        """.trimIndent()

        // 4. KONEKSI KE SERVER GROQ (Bypass limit Gemini yang pelit)
        try {
            // Menggunakan endpoint resmi chat completion Groq Cloud
            val url = URL("https://api.groq.com/openai/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey") // Menyuntikkan token Groq
            conn.doOutput = true

            // Struktur JSON standar OpenAI/Groq yang sangat stabil
            val jsonBody = JSONObject().apply {
                put("model", "llama3-8b-8192") // Menggunakan model Llama 3 super kencang & gratis
                
                val messagesArray = org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userMessage)
                    })
                }
                put("messages", messagesArray)
                put("temperature", 0.7)
            }

            conn.outputStream.use { os ->
                os.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
            }

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val responseStr = reader.readText()
                val jsonResponse = JSONObject(responseStr)
                
                // Ekstraksi teks jawaban dari format OpenAI/Groq JSON
                val choice = jsonResponse.getJSONArray("choices").getJSONObject(0)
                val textResponse = choice.getJSONObject("message").getString("content")
                return@withContext textResponse
            } else {
                val errorReader = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream))
                val errorLog = errorReader.readText()
                return@withContext "Error AI Server (Code ${conn.responseCode}). Silakan pastikan API Key Groq dimasukkan dengan benar di menu Pengaturan."
            }
        } catch (e: Exception) {
            return@withContext "Gagal terhubung ke modul AI. Pastikan internet aktif. Detail: ${e.message}"
        }
    }
}
