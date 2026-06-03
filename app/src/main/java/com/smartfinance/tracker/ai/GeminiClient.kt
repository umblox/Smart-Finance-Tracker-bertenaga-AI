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
            return@withContext "⚠️ API Key Groq belum diatur di menu Pengaturan."
        }

        val db = AppDatabase.getDatabase(context)
        val debts = db.debtDao().getAllDebts().first()
        val debtContext = java.lang.StringBuilder()
        debts.filter { !it.isPaid }.forEach { 
            debtContext.append("- ID: ${it.id}, Kontak: ${it.contactName}, Sisa: Rp ${it.remainingAmount}, Tipe: ${it.type}\n")
        }

        val systemPrompt = """
            Anda adalah bot akuntan cerdas database SQLite. Ambil keputusan biner kaku.
            
            DAFTAR UTANG PIUTANG AKTIF DI HP USER:
            $debtContext

            TUGAS UTAMA:
            1. Jika user mencatat pemasukan/pengeluaran biasa (ex: "gajian 775000 nih"), balas ramah lalu taruh tag:
               <EXEC>{"action_type":"TRANSACTION", "amount":775000, "type":"INCOME", "category_id":1, "category_name":"Gaji & Pendapatan", "clean_note":"GAJIAN"}</EXEC>
            
            2. Jika user mencatat cicilan hutang (ex: "dani membayar cicilan hutang 30000"), lihat daftar di atas. Temukan nama DANI, ambil ID-nya (misal ID 2). Wajib buat tag:
               <EXEC>{"action_type":"DEBT_PAYMENT", "debt_id":2, "pay_amount":30000}</EXEC>

            Hasilkan tag <EXEC>...</EXEC> tepat di bagian bawah kalimat balasan Anda! Jangan dihilangkan!
        """.trimIndent()

        try {
            val url = URL("https://api.groq.com/openai/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true

            val jsonBody = JSONObject().apply {
                put("model", "llama-3.1-8b-instant")
                val messagesArray = org.json.JSONArray().apply {
                    put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                    put(JSONObject().apply { put("role", "user"); put("content", userMessage) })
                }
                put("messages", messagesArray)
                put("temperature", 0.0) // Set ke 0 mutlak agar AI patuh aturan, tidak ngawur/berhalusinasi
            }

            conn.outputStream.use { os -> os.write(jsonBody.toString().toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val rawResponse = JSONObject(reader.readText()).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content").trim()
                
                return@withContext assistant.parseAndExecuteRawAiResponse(rawResponse)
            } else {
                return@withContext "⚠️ Hubungan ke Groq terputus (HTTP ${conn.responseCode}). Periksa kembali kesahihan API Key Anda."
            }
        } catch (e: Exception) {
            return@withContext "⚠️ Server Groq sibuk, silakan ulangi."
        }
    }
}
