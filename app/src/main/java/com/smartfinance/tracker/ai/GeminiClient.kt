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

        // Ambil info tanggal hari ini sebagai jangkar acuan kalkulasi waktu AI
        val sdfToday = SimpleDateFormat("yyyy-MM-dd (EEEE)", Locale("id", "ID"))
        val todayString = sdfToday.format(Date())

        val rawSystemPrompt = """
            Anda adalah core engine AI finansial akuntansi premium. Anda WAJIB merespons HANYA dengan objek JSON murni tanpa markdown (tanpa ```json).
            
            JANGKAR ACUAN WAKTU HARI INI:
            $todayString

            KATEGORI PENGIKAT DI SQLITE:
            CONTEXT_CATEGORIES

            DAFTAR PINJAMAN AKTIF DI SQLITE:
            CONTEXT_DEBTS

            ⚠️ ATURAN DETEKSI WAKTU & KALENDER (MANDATORI):
            - Analisis informasi waktu dari pesan user (contoh: "kemarin", "2 hari lalu", "januari lalu", "tanggal 12", "besok").
            - Hitung tanggalnya berdasarkan JANGKAR ACUAN WAKTU HARI INI yang diberikan di atas.
            - Masukkan hasil kalkulasi tanggal tersebut ke dalam field 'transaction_date' dengan format kaku 'YYYY-MM-DD'.
            - Jika user tidak menyebutkan keterangan waktu, isi field 'transaction_date' dengan tanggal hari ini.

            ⚠️ ATURAN EVALUASI LOGIKA KLASIFIKASI:
            - "Gajian" / "Pemasukan" -> type: 'INCOME', Kategori ID: 1 (Gaji & Pendapatan). JANGAN PERNAH terbalik jadi EXPENSE!
            - "Beli" / "Pengeluaran" / "Bayar makan" -> type: 'EXPENSE'.
            - "Saya meminjam KEPADA [Nama]" -> action_type: 'DEBT_RECORD', debt_type: 'DEBT' (Hutang), Kategori ID: 12.
            - "[Nama] meminjam DARI SAYA" / "Saya meminjamkan KE [Nama]" -> action_type: 'DEBT_RECORD', debt_type: 'RECEIVABLE' (Piutang), Kategori ID: 13.

            STRUKTUR JSON YANG WAJIB DIHASILKAN:
            {
              'action_type': 'TRANSACTION' atau 'DEBT_RECORD' atau 'DEBT_PAYMENT',
              'amount': 2300000,
              'type': 'INCOME' atau 'EXPENSE',
              'category_id': 1,
              'category_name': 'Gaji & Pendapatan',
              'clean_note': 'GAJIAN UTAMA',
              'transaction_date': 'YYYY-MM-DD',
              'ai_response': 'Kalimat balasan premium yang ramah, ekspresif, menyebutkan nominal riil, info tanggal transaksi, dan nama kontak jika ada.'
            }
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
                put("temperature", 0.0) // Kunci ke 0 agar AI patuh matematika kalender
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
