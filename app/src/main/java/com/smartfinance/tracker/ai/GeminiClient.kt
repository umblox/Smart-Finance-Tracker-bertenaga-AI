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
        // 1. Cek interceptor lokal terlebih dahulu (untuk fitur cek saldo cepat)
        val localResponse = assistant.processNaturalLanguage(userMessage)
        if (!localResponse.contains("Format kurang spesifik")) {
            return@withContext localResponse
        }

        // 2. Ambil API Key Gemini dari SharedPreferences Pengaturan
        val prefs = context.getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "") ?: ""
        if (apiKey.isEmpty()) {
            return@withContext "API Key Gemini belum diatur di menu Pengaturan. Silakan masukkan API Key Anda terlebih dahulu."
        }

        // 3. Ambil data transaksi nyata dari database untuk dijadikan bahan konteks otak AI
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
            
            Tugas Anda adalah menjawab pertanyaan keuangan, menganalisis pengeluaran mereka, atau memberikan rekomendasi penghematan berdasarkan data di atas. Jawablah dengan ramah, santun, dan gunakan format Bahasa Indonesia yang rapi.
        """.trimIndent()

        // 4. Koneksi HTTP API ke Endpoint Resmi Google Gemini
        try {
            // PERBAIKAN: Mengubah model target dari 'gemini-pro' menjadi 'gemini-2.5-flash'
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val jsonBody = JSONObject().apply {
                val contentsArray = org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", org.json.JSONArray().apply {
                            put(JSONObject().put("text", "$systemPrompt\n\nPertanyaan Pengguna: $userMessage"))
                        })
                    })
                }
                put("contents", contentsArray)
            }

            conn.outputStream.use { os ->
                os.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
            }

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val responseStr = reader.readText()
                val jsonResponse = JSONObject(responseStr)
                val candidate = jsonResponse.getJSONArray("candidates").getJSONObject(0)
                val textResponse = candidate.getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                return@withContext textResponse
            } else {
                return@withContext "Error API Gemini (Code ${conn.responseCode}): Harap periksa kembali validitas API Key Anda di menu Pengaturan."
            }
        } catch (e: Exception) {
            return@withContext "Gagal terhubung ke server Gemini Google. Pastikan HP Anda terkoneksi internet. Detail: ${e.message}"
        }
    }
}
