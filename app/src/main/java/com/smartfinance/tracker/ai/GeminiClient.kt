package com.smartfinance.tracker.ai

import android.content.Context
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.BuildConfig
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
        val apiKey = BuildConfig.GROQ_API_KEY

        if (apiKey.isEmpty()) {
            return@withContext "⚠️ API Key Groq aman tidak ditemukan dalam sistem build."
        }

        val db = AppDatabase.getDatabase(context)
        
        val categories = db.categoryDao().getAllCategories().first()
        val catContext = java.lang.StringBuilder()
        categories.forEach { catContext.append("- ID: ${it.id}, Nama: ${it.name}, Tipe: ${it.type}\n") }

        val debts = db.debtDao().getAllDebts().first()
        val debtContext = java.lang.StringBuilder()
        debts.filter { !it.isPaid }.forEach { 
            debtContext.append("- ID: ${it.id}, Kontak: ${it.contactName}, Sisa: Rp ${it.remainingAmount}, Tipe: ${it.type}\n")
        }

        val rawSystemPrompt = """
            Anda adalah core engine AI finansial akuntansi premium. Anda WAJIB merespons HANYA dengan objek JSON murni tanpa markdown (```json).
            
            KATEGORI PENGIKAT DI SQLITE:
            CONTEXT_CATEGORIES

            DAFTAR PINJAMAN AKTIF DI SQLITE:
            CONTEXT_DEBTS

            ⚠️ MATRIKS KAKU SUBJEK UTANG PIUTANG (JANGAN PERNAH TERBALIK!):
            - Kalimat: "Saya meminjam uang KEPADA Dani" / "Saya ngutang KEPADA Dani" -> Ini MUTLAK action_type 'DEBT_RECORD', debt_type 'DEBT' (Hutang Saya). Kategori ID wajib 12. Kalimat balasan wajib menyebutkan bahwa Anda berhutang kepada kontak tersebut.
            - Kalimat: "Dani meminjam uang KEPADA SAYA" / "Saya meminjamkan uang KE Dani" -> Ini MUTLAK action_type 'DEBT_RECORD', debt_type 'RECEIVABLE' (Piutang Saya). Kategori ID wajib 13.

            📋 ATURAN BALASAN MANUSIA ('ai_response'):
            Jangan membalas dengan kalimat kaku seperti "Pengeluaran dicatat". Buat kalimat yang bervariasi, detail, ekspresif, menyebutkan nominal riil, nama kontak jika ada, dan beri catatan atau emotikon yang relevan selevel aplikasi keuangan premium.

            CONTOH FORMAT JSON YANG WAJIB ANDA IKUTI:
            - Jika User Ngutang: {'action_type':'DEBT_RECORD', 'amount':20000, 'contact_name':'DANI', 'debt_type':'DEBT', 'ai_response':'Catatan tersimpan, Mam! Anda telah mencatat hutang baru kepada DANI sebesar Rp 20.000. Jangan lupa dilunasi sebelum jatuh tempo ya!'}
            - Jika Transaksi Makan: {'action_type':'TRANSACTION', 'amount':15000, 'type':'EXPENSE', 'category_id':2, 'category_name':'Makanan & Minuman', 'clean_note':'BELI NASI GORENG', 'ai_response':'Nasi gorengnya kelihatannya enak! Pengeluaran makan siang sebesar Rp 15.000 sudah berhasil diamankan ke sistem.'}
        """.trimIndent()

        val finalSystemPrompt = rawSystemPrompt
            .replace("CONTEXT_CATEGORIES", catContext.toString())
            .replace("CONTEXT_DEBTS", debtContext.toString())
            .replace("'", "\"")

        try {
            val url = URL("[https://api.groq.com/openai/v1/chat/completions](https://api.groq.com/openai/v1/chat/completions)")
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
                    put(JSONObject().apply { put("role", "system"); put("content", finalSystemPrompt) })
                    put(JSONObject().apply { put("role", "user"); put("content", userMessage) })
                }
                put("messages", messagesArray)
                put("temperature", 0.2) // Naikkan sedikit agar bahasanya luwes tapi tetap patuh skema JSON
                put("response_format", JSONObject().apply { put("type", "json_object") })
            }

            conn.outputStream.use { os -> os.write(jsonBody.toString().toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val rawResponse = JSONObject(reader.readText()).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content").trim()
                
                return@withContext assistant.parseAndExecuteRawAiResponse(rawResponse)
            } else {
                return@withContext "⚠️ Hubungan ke Groq terputus (HTTP ${conn.responseCode})"
            }
        } catch (e: Exception) {
            return@withContext "⚠️ Gangguan Jaringan Lokal: ${e.localizedMessage ?: "Timeout"}"
        }
    }
}
