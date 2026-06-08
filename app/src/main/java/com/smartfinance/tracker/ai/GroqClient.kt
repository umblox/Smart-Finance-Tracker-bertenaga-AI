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
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GroqClient(private val context: Context, private val assistant: FinancialAssistant) {

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

        val sdfToday = SimpleDateFormat("yyyy-MM-dd (EEEE)", Locale("id", "ID"))
        val todayString = sdfToday.format(Date())

        val rawSystemPrompt = """
            Anda adalah core engine AI finansial akuntansi premium. Anda WAJIB merespons HANYA dengan objek JSON murni yang valid tanpa markdown block (JANGAN gunakan ```json).
            
            JANGKAR ACUAN WAKTU HARI INI:
            ${'$'}todayString

            KATEGORI PENGIKAT DI SQLITE:
            CONTEXT_CATEGORIES

            DAFTAR PINJAMAN AKTIF DI SQLITE:
            CONTEXT_DEBTS

            ⚠️ ATURAN KALENDER DAN BULAN:
            - Hitung tanggal transaksi berdasarkan JANGKAR ACUAN WAKTU HARI INI.

            ⚠️ ATURAN MUTLAK LOGIKA UTANG-PIUTANG (JANGAN PERNAH TERBALIK!):
            1. Jika kalimat bermakna "Orang lain meminjam uang ke SAYA" atau "SAYA meminjamkan uang ke orang lain" (Contoh: "Arianto meminjam uang kepada saya sebesar 50000"):
               - Set 'action_type' menjadi 'DEBT_RECORD'
               - Set 'debt_type' menjadi 'RECEIVABLE' (Artinya: SAYA memberi piutang/tagihan, orang itu utang ke SAYA).
               - Di dalam 'ai_response', Anda WAJIB merespons dengan format: "[Nama Orang] meminjam uang kepada Anda sebesar [Nominal]. Ini berarti Anda memiliki piutang kepada [Nama Orang]."

            2. Jika kalimat bermakna "SAYA meminjam uang dari orang lain" atau "SAYA berhutang kepada orang lain" (Contoh: "Saya meminjam uang ke Arianto sebesar 50000"):
               - Set 'action_type' menjadi 'DEBT_RECORD'
               - Set 'debt_type' menjadi 'DEBT' (Artinya: SAYA berhutang, SAYA wajib membayar nanti).
               - Di dalam 'ai_response', Anda WAJIB merespons dengan format: "Anda meminjam uang kepada [Nama Orang] sebesar [Nominal]. Ini berarti Anda memiliki utang kepada [Nama Orang]."

            3. Jika kalimat bermakna "Orang lain membayar cicilan utangnya ke SAYA" atau "SAYA menagih/menerima uang pembayaran utang":
               - Set 'action_type' menjadi 'DEBT_PAYMENT'

            STRUKTUR KAKU OUTPUT JSON:
            {
              "action_type": "TRANSACTION" / "DEBT_RECORD" / "DEBT_PAYMENT" / "VIEW_REPORT" / "CHAT_ONLY",
              "ai_response": "Kalimat balasan manusiawi yang rapi sesuai aturan mutlak di atas.",
              "transactions": [
                {
                  "amount": 50000,
                  "type": "EXPENSE" / "INCOME",
                  "contact_name": "ARIANTO",
                  "debt_type": "DEBT" / "RECEIVABLE" / "NONE",
                  "debt_id": -1,
                  "pay_amount": 0,
                  "category_id": 104,
                  "category_name": "Piutang",
                  "clean_note": "MEMINJAMKAN UANG",
                  "transaction_date": "YYYY-MM-DD"
                }
              ]
            }
        """.trimIndent()

        val finalSystemPrompt = rawSystemPrompt
            .replace("CONTEXT_CATEGORIES", catContext.toString())
            .replace("CONTEXT_DEBTS", debtContext.toString())
            .replace("'", "\"")

        try {
            val uri = URI("https", "api.groq.com", "/openai/v1/chat/completions", null)
            val url = uri.toURL()

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
                put("temperature", 0.1)
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
