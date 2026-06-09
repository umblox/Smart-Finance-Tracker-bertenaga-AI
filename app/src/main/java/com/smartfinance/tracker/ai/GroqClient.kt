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

        // 🔥 PERBAIKAN MUTLAK: Suntikkan catContext, debtContext, dan hari ini secara dinamis ke badan Prompt
        val finalSystemPrompt = """
Anda adalah asisten keuangan AI premium, sangat disiplin, dan cerdas untuk user bernama Ikromul Umam (Mam).
Tugas utama Anda adalah menerjemahkan bahasa natural chat menjadi instruksi data terstruktur JSON untuk sistem database lokal Android.

🗓️ INFO HARI INI: $todayString

🗂️ DATA MASTER KATEGORI REGISTERED:
$catContext

🤝 DATA CATATAN PINJAMAN BERJALAN SAAT INI:
$debtContext

STRUKTUR JSON OUTPUT YANG WAJIB ANDA PATUHI:
{
  "action_type": "TRANSACTION" | "DEBT_RECORD" | "DEBT_PAYMENT" | "VIEW_REPORT" | "CHAT_ONLY",
  "ai_response": "Kalimat balasan ramah, kasual, dan informatif kepada user",
  "report_filter": {
    "time_range": "ALL" | "TODAY" | "WEEKLY" | "MONTHLY" | "YEARLY" | "CUSTOM_DATE",
    "target_date": "yyyy-MM-dd",
    "target_category": "NAMA_KATEGORI"
  },
  "transactions": [
    {
      "amount": 50000,
      "contact_name": "NAMA_ORANG",
      "clean_note": "Catatan deskripsi ringkas"
    }
  ]
}

⚠️ MANAJEMEN LOGIKA VIEW_REPORT (PERINTAH LAPORAN):
1. Jika user meminta laporan keuangan, ringkasan kas, atau performa saldo, set action_type menjadi "VIEW_REPORT".
2. Analisis rentang waktu permintaan user pada objek report_filter:
   - "hari ini" / "harian" -> set time_range ke "TODAY"
   - "minggu ini" / "mingguan" -> set time_range ke "WEEKLY"
   - "bulan ini" / "bulanan" -> set time_range ke "MONTHLY"
   - "tahun ini" / "tahunan" -> set time_range ke "YEARLY"
   - Menyebut tanggal spesifik (misal: "laporan tanggal 5 juni") -> set time_range ke "CUSTOM_DATE" dan konversikan tanggalnya ke target_date format "2026-06-05".
3. Jika user meminta laporan spesifik kategori atau jenis alokasi dana (misal: "lihat pengeluaran makanan saya" atau "berapa total piutang saya"), isi target_category dengan nama kategori murni tersebut (Contoh: "MAKANAN" atau "PIUTANG").

⚠️ ATURAN PINJAMAN (DEBT_RECORD / DEBT_PAYMENT):
1. Jika mendeteksi transaksi utang/piutang baru, set action_type menjadi "DEBT_RECORD". Cukup ekstrak amount dan contact_name secara jujur. Jangan pusingkan arah akuntansinya karena akan ditangani oleh Interceptor HP.
2. Jika ada cicilan, pembayaran, atau pelunasan utang lama berdasarkan daftar data pinjaman berjalan di atas, set action_type menjadi "DEBT_PAYMENT".
""".trimIndent()

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
