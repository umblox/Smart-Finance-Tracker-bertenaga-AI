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

            ⚠️ MATRIKS KAKU UTANG PIUTANG (JANGAN TERBALIK!):
            1. 'action_type': 'DEBT_RECORD' (Pencatatan Utang/Piutang Baru)
               - Jika SAYA MEMINJAM uang ke orang lain / SAYA UTANG ke orang lain -> 'debt_type' WAJIB 'DEBT' (Kategori ID: 12).
               - Jika ORANG LAIN MEMINJAM uang ke saya / SAYA MEMINJAMKAN uang -> 'debt_type' WAJIB 'RECEIVABLE' (Kategori ID: 13).
            
            2. 'action_type': 'DEBT_PAYMENT' (Bayar/Cicil Utang Lama)
               - Jika saya bayar utang saya ke orang lain -> Ambil ID kontak dari daftar bertipe 'DEBT'.
               - Jika orang lain bayar utangnya ke saya -> Ambil ID kontak dari daftar bertipe 'RECEIVABLE'.

            3. 'action_type': 'VIEW_REPORT' (User ingin melihat grafik/laporan/analisis keuangan)
               - Jika user meminta laporan, ringkasan, atau ingin tahu total pengeluaran/pendapatan, set 'action_type' menjadi 'VIEW_REPORT'.

            ⚠️ STRUKTUR KAKU OUTPUT JSON:
            {
              'action_type': 'TRANSACTION' / 'DEBT_RECORD' / 'DEBT_PAYMENT' / 'VIEW_REPORT',
              'amount': 50000,
              'type': 'EXPENSE' / 'INCOME',
              'contact_name': 'NAMA_ORANG',
              'debt_type': 'DEBT' / 'RECEIVABLE',
              'debt_id': -1,
              'pay_amount': 0,
              'category_id': 2,
              'category_name': 'Makanan & Minuman',
              'clean_note': 'CATATAN BERSIH',
              'transaction_date': 'YYYY-MM-DD',
              'ai_response': 'Tulis kalimat respons manusiawi, detail, ramah, dan jelaskan aksi keuangan yang terjadi secara lengkap.'
            }
        """.trimIndent()

        val finalSystemPrompt = rawSystemPrompt
            .replace("CONTEXT_CATEGORIES", catContext.toString())
            .replace("CONTEXT_DEBTS", debtContext.toString())
            .replace("'", "\"")

        try {
            // SIKAT SENSOR MARXDOWN: Alamat URL murni tanpa tanda kurung siku/biasa
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
                put("temperature", 0.2)
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
