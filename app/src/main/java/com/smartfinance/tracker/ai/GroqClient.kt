package com.smartfinance.tracker.ai

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.smartfinance.tracker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GroqClient(private val context: Context, private val assistant: FinancialAssistant) {

    private val firestore = FirebaseFirestore.getInstance()

    suspend fun sendMessageToAI(userMessage: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GROQ_API_KEY

        if (apiKey.isEmpty()) {
            return@withContext "⚠️ API Key Groq aman tidak ditemukan dalam sistem build."
        }

        val catContext = java.lang.StringBuilder()
        val debtContext = java.lang.StringBuilder()

        try {
            val categorySnapshot = firestore.collection("categories").get().await()
            for (doc in categorySnapshot.documents) {
                val id = doc.getLong("id") ?: 0L
                val name = doc.getString("name") ?: "Tanpa Nama"
                val type = doc.getString("type") ?: "EXPENSE"
                catContext.append("- ID: $id, Nama: $name, Tipe: $type\n")
            }

            val debtSnapshot = firestore.collection("debts").get().await()
            for (doc in debtSnapshot.documents) {
                val isPaid = doc.getBoolean("isPaid") ?: false
                if (!isPaid) {
                    val contactName = doc.getString("contactName") ?: "TEMAN"
                    val amount = doc.getDouble("amount") ?: 0.0
                    val remaining = doc.getDouble("remainingAmount") ?: 0.0
                    val type = doc.getString("type") ?: "DEBT"
                    debtContext.append("- Kontak: $contactName, Sisa Riil Terutang: Rp $remaining, Jenis: $type\n")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // ✅ FIX TANGGAL: Ngasih tau Groq jam dan tahun sekarang biar dia bisa ngitung tanggal mundur dengan pas
        val sdfToday = SimpleDateFormat("dd-MM-yyyy HH:mm (EEEE)", Locale("id", "ID"))
        val todayString = sdfToday.format(Date())

        val systemPrompt = """
            Anda adalah Asisten Finansial Pribadi untuk aplikasi Smart Finance Tracker milik Ikromul Umam.
            Nama panggilan user adalah 'Mam'. DILARANG KERAS menggunakan kata 'Anda', ganti dengan kata 'Mam'.
            Gunakan bahasa Indonesia yang santai, luwes, profesional, dan akrab.
            
            🗓️ WAKTU SAAT INI: $todayString
            [KATEGORI MASTER]: $catContext
            [DATA UTANG AKTIF]: $debtContext
            
            ATURAN KLASIFIKASI & RESPON:
            1. VIEW_REPORT (PERMINTAAN LAPORAN): Jika Mam meminta laporan (harian/mingguan/bulanan/kategori), kembalikan action_type: "VIEW_REPORT". KOSONGKAN 'ai_response' karena Kotlin yang akan merender teks laporannya.
            2. CHAT_ONLY (NGOBROL BIASA): Jika Mam bertanya daftar kategori, buatlah list berderet ke bawah menggunakan bullet point dan emoji yang rapi di 'ai_response'. Jika Mam bertanya status utang, jawab dengan luwes tanpa array transactions.
            3. TRANSAKSI & PINJAMAN: Jika Mam menyuruh mencatat uang. Perhatikan SPOK! 
               - Jika Mam meminjamkan uang ke orang lain (PIUTANG / RECEIVABLE), balas di 'ai_response' dengan kalimat: "Siap Mam, piutang untuk [Nama] sebesar [Nominal] sudah saya catat." (JANGAN PERNAH bilang "utang dari [Nama]").
               - Jika Mam ngutang ke orang (DEBT), balas: "Siap Mam, utang kepada [Nama] sudah dicatat."
               - Pastikan Anda mengekstrak tanggal dan jam dari perintah Mam ke format "dd-MM-yyyy HH:mm" di dalam JSON 'transaction_date'.
               
            FORMAT JSON WAJIB:
            {
              "action_type": "CHAT_ONLY" | "TRANSACTION" | "DEBT_RECORD" | "DEBT_PAYMENT" | "VIEW_REPORT",
              "ai_response": "Teks balasan asisten yang luwes (Kosongkan jika VIEW_REPORT)",
              "report_filter": {
                 "time_range": "TODAY" | "WEEKLY" | "MONTHLY" | "YEARLY" | "CUSTOM_DATE" | "ALL",
                 "target_date": "dd-MM-yyyy",
                 "target_category": "NAMA_KATEGORI_JIKA_ADA",
                 "target_keyword": "KATA_KUNCI_BARANG_JIKA_ADA"
              },
              "transactions": [
                 {
                   "amount": 50000,
                   "type": "EXPENSE" | "INCOME",
                   "category_id": 15,
                   "category_name": "Nama Kategori",
                   "clean_note": "Catatan ringkas",
                   "contact_name": "NAMA_KONTAK",
                   "debt_type": "DEBT" | "RECEIVABLE",
                   "is_new_category": false,
                   "transaction_date": "dd-MM-yyyy HH:mm" // Isi sesuai perintah Mam. Kosongkan jika tidak disebutkan.
                 }
              ]
            }
        """.trimIndent()

        try {
            val url = URI("https://api.groq.com/openai/v1/chat/completions").toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.doOutput = true

            val messagesArray = JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                put(JSONObject().apply { put("role", "user"); put("content", userMessage) })
            }

            val jsonBody = JSONObject().apply {
                put("model", "llama-3.3-70b-versatile")
                put("messages", messagesArray)
                put("temperature", 0.0)
                put("response_format", JSONObject().apply { put("type", "json_object") })
            }

            conn.outputStream.use { os -> os.write(jsonBody.toString().toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val rawResponse = reader.readText()
                val extractedContent = JSONObject(rawResponse).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content").trim()
                
                return@withContext assistant.parseAndExecuteRawAiResponse(extractedContent)
            } else {
                val errorReader = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream))
                val errorText = errorReader.readText()
                return@withContext "⚠️ Hubungan ke Groq terputus (HTTP ${conn.responseCode}): $errorText"
            }
        } catch (e: Exception) {
            return@withContext "⚠️ Gangguan Jaringan Lokal: ${e.localizedMessage ?: "Timeout"}"
        }
    }
}
