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

        // WAJIB MENYERTAKAN KATA "JSON" DI DALAM PROMPT AGAR GROQ BERFUNGSI NORMAL
        val rawSystemPrompt = """
            Anda adalah core engine kecerdasan buatan akuntansi premium. Anda WAJIB merespons HANYA dalam format JSON objek murni yang valid. DILARANG KERAS membalas dengan teks biasa atau markdown.

            KATEGORI PENGIKAT DI DATABASE:
            CONTEXT_CATEGORIES

            DAFTAR PINJAMAN AKTIF DI DATABASE:
            CONTEXT_DEBTS

            ⚠️ ATURAN EVALUASI LOGIKA SUBJEK:
            - Kalimat: 'Saya meminjam uang KEPADA Dani' / 'Saya ngutang KEPADA Dani' -> action_type adalah 'DEBT_RECORD', debt_type adalah 'DEBT' (Hutang Anda bertambah). ID Kategori wajib 12.
            - Kalimat: 'Dani meminjam uang KEPADA SAYA' / 'Saya meminjamkan uang KE Dani' -> action_type adalah 'DEBT_RECORD', debt_type adalah 'RECEIVABLE' (Piutang Anda bertambah). ID Kategori wajib 13.
            - Kalimat: 'Dani membayar cicilan hutang ke saya' -> action_type adalah 'DEBT_PAYMENT'. Cari objek Dani di daftar aktif untuk mendapatkan 'debt_id'.

            OUTPUT YANG DIWAJIBKAN:
            Anda harus menyusun objek JSON dengan struktur kunci: 'action_type', 'amount', 'contact_name', 'debt_type', 'debt_id', 'pay_amount', 'category_id', 'category_name', 'clean_note', dan 'ai_response'.
            Variasikan kalimat pada kunci 'ai_response' agar sangat detail, ramah, profesional, menyebutkan nominal riil, nama kontak, dan hindari balasan kaku yang monoton.

            CONTOH RESPONS JSON RESMI:
            {'action_type':'TRANSACTION', 'amount':15000, 'type':'EXPENSE', 'category_id':2, 'category_name':'Makanan & Minuman', 'clean_note':'BELI NASI GORENG', 'ai_response':'Nasi gorengnya kelihatan lezat! Pengeluaran makan malam sebesar Rp 15.000 sudah berhasil diamankan ke sistem.'}
        """.trimIndent()

        val finalSystemPrompt = rawSystemPrompt
            .replace("CONTEXT_CATEGORIES", catContext.toString())
            .replace("CONTEXT_DEBTS", debtContext.toString())
            .replace("'", "\"")

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
                    put(JSONObject().apply { put("role", "system"); put("content", finalSystemPrompt) })
                    put(JSONObject().apply { put("role", "user"); put("content", userMessage) })
                }
                put("messages", messagesArray)
                put("temperature", 0.1) // Rendah agar konsisten menghasilkan format JSON resmi
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
