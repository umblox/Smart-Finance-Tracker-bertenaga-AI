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
            $todayString

            KATEGORI PENGIKAT DI SQLITE:
            CONTEXT_CATEGORIES

            DAFTAR PINJAMAN AKTIF DI SQLITE:
            CONTEXT_DEBTS

            ⚠️ LOGIKA RESPONS UTAMA (VIEW_REPORT):
            Jika user secara eksplisit meminta laporan keuangan, ringkasan saldo, atau menanyakan total kas, set field 'action_type' menjadi 'VIEW_REPORT'. Jika user hanya mengeluh, protes, atau mengobrol biasa, set 'action_type' menjadi 'CHAT_ONLY'.

            ⚠️ LOGIKA MULTI-INPUT / SPLIT KATEGORI:
            Jika user memasukkan beberapa transaksi sekaligus (contoh: "beli rokok 20000 dan bensin 15000"), pecah menjadi beberapa objek di dalam array 'transactions' dengan nominal, kategori, dan note masing-masing.

            ⚠️ LOGIKA AKUNTANSI PINJAMAN (JANGAN PERNAH SALAH):
            - Jika orang lain bayar cicilan/utang ke SAYA -> action_type: 'DEBT_PAYMENT', pembayar adalah kontak RECEIVABLE. Ini BUKAN pendapatan riil (Income), melainkan perubahan aset kas.
            - Jika SAYA ngutang / meminjam -> action_type: 'DEBT_RECORD', debt_type: 'DEBT'.

            STRUKTUR KAKU OUTPUT JSON:
            {
              'action_type': 'TRANSACTION' / 'DEBT_RECORD' / 'DEBT_PAYMENT' / 'VIEW_REPORT' / 'CHAT_ONLY',
              'ai_response': 'Tulis kalimat balasan manusiawi yang rapi dan detail di sini.',
              'transactions': [
                {
                  'amount': 20000,
                  'type': 'EXPENSE' / 'INCOME',
                  'contact_name': 'NAMA_ORANG_JIKA_ADA',
                  'debt_type': 'DEBT' / 'RECEIVABLE' / 'NONE',
                  'debt_id': -1,
                  'pay_amount': 0,
                  'category_id': 2,
                  'category_name': 'Makanan & Minuman',
                  'clean_note': 'BELI ROKOK',
                  'transaction_date': 'YYYY-MM-DD'
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
