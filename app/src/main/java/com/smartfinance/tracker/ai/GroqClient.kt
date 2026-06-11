package com.smartfinance.tracker.ai

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.smartfinance.tracker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
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

    private val firestore = FirebaseFirestore.getInstance()

    suspend fun sendMessageToAI(userMessage: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GROQ_API_KEY

        if (apiKey.isEmpty()) {
            return@withContext "⚠️ API Key Groq aman tidak ditemukan dalam sistem build."
        }

        val catContext = java.lang.StringBuilder()
        val debtContext = java.lang.StringBuilder()
        val txContext = java.lang.StringBuilder()

        try {
            // 1. Ambil Data Master Kategori Terdaftar
            val categorySnapshot = firestore.collection("categories").get().await()
            for (doc in categorySnapshot.documents) {
                val id = doc.getLong("id") ?: 0L
                val name = doc.getString("name") ?: "Tanpa Nama"
                val type = doc.getString("type") ?: "EXPENSE"
                catContext.append("- ID: $id, Nama: $name, Tipe: $type\n")
            }

            // 2. Ambil Data Catatan Pinjaman Berjalan Aktif
            val debtSnapshot = firestore.collection("debts").get().await()
            for (doc in debtSnapshot.documents) {
                val isPaid = doc.getBoolean("isPaid") ?: false
                if (!isPaid) {
                    val contactName = doc.getString("contactName") ?: "TEMAN"
                    val remainingAmount = doc.getDouble("remainingAmount") ?: 0.0
                    val type = doc.getString("type") ?: "DEBT"
                    debtContext.append("- Kontak: $contactName, Sisa: Rp $remainingAmount, Tipe: $type\n")
                }
            }

            // 3. Tarik Seluruh Riwayat Transaksi Riil untuk Analisis Matematika Laporan
            val txSnapshot = firestore.collection("transactions").get().await()
            val sdfTx = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            for (doc in txSnapshot.documents) {
                val amount = doc.getDouble("amount") ?: 0.0
                val type = doc.getString("type") ?: "EXPENSE"
                val categoryName = doc.getString("categoryName") ?: "Umum"
                val note = doc.getString("note") ?: ""
                val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                val dateStr = sdfTx.format(Date(timestamp))
                txContext.append("- [$dateStr] Kategori: $categoryName, Tipe: $type, Jumlah: Rp $amount, Catatan: $note\n")
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        val sdfToday = SimpleDateFormat("yyyy-MM-dd (EEEE)", Locale("id", "ID"))
        val todayString = sdfToday.format(Date())

        // 🔥 MASTA PROMPT REVOLUSI: Menggabungkan Kecerdasan Analisis Laporan Finansial + SPOK Indonesia Berlapis
        val finalSystemPrompt = """
Anda adalah asisten keuangan AI premium, sangat cerdas, analitis, dan solutif untuk user bernama Ikromul Umam (Mam).
Tugas utama Anda adalah mengelola data keuangan, membuat laporan mendalam, dan memetakan teks transaksi ke JSON secara akurat tanpa terbalik.

🗓️ INFO HARI INI: $todayString

🗂️ DATA MASTER KATEGORI REGISTERED:
${if (catContext.isEmpty()) "- Belum ada kategori" else catContext.toString()}

🤝 DATA CATATAN PINJAMAN BERJALAN SAAT INI:
${if (debtContext.isEmpty()) "- Tidak ada utang/piutang aktif" else debtContext.toString()}

📊 DATA SELURUH RIWAYAT TRANSAKSI RIIL (Wajib hitung laporan harian, mingguan, bulanan, tahunan dari sini):
${if (txContext.isEmpty()) "- Belum ada riwayat mutasi transaksi keuangan" else txContext.toString()}

⚠️ ATURAN LAPORAN FINANSIAL:
- Jika user meminta laporan (Harian, Mingguan, Bulanan, Tahunan, atau per kategori/keyword barang spesifik), Anda WAJIB menjumlahkan data mutasi di atas secara matematis dan akurat, lalu tulis rincian kalkulasinya di "ai_response" menggunakan Markdown yang rapi. Setel "action_type": "VIEW_REPORT".

⚠️ ATURAN TATA BAHASA UTANG PIUTANG (SPOK INDONESIA):
- KALIMAT PIUTANG (RECEIVABLE): Jika orang lain meminjam uang ke user (Contoh: "muslim rantau meminjam uang kepada saya"). Setel "action_type": "DEBT_RECORD", dan setel "debt_type": "RECEIVABLE".
- KALIMAT UTANG (DEBT): Jika user meminjam uang dari orang lain. Setel "action_type": "DEBT_RECORD", dan setel "debt_type": "DEBT".

⚠️ ATURAN KHUSUS KATEGORI & SUB-KATEGORI BARU (ANTI-BENTROK):
1. Cari kategori terdekat dari DATA MASTER KATEGORI REGISTERED di atas. Jika ada yang mendekati, WAJIB gunakan ID dan Nama dari master tersebut!
2. JIKALAU TRANSAKSI USER BENAR-BENAR BARU DAN TIDAK ADA KATEGORI YANG MENDEKATI:
   - Buatkan Nama Kategori Baru yang relevan (Gunakan format HURUF BESAR di awal).
   - Berikan "category_id" acak baru di atas angka 200 (Contoh: 205, 210) agar tidak bentrok dengan ID master.
   - WAJIB setel properti "is_new_category" menjadi true! (Jika menggunakan kategori master lama, setel menjadi false).

STRUKTUR JSON OUTPUT YANG WAJIB ANDA PATUHI:
{
  "action_type": "TRANSACTION" | "DEBT_RECORD" | "DEBT_PAYMENT" | "VIEW_REPORT" | "CHAT_ONLY",
  "ai_response": "Tuliskan hasil laporan finansial mendalam atau teks konfirmasi transaksi yang lengkap dan indah tanpa terpotong.",
  "report_filter": {
    "time_range": "ALL" | "TODAY" | "WEEKLY" | "MONTHLY" | "YEARLY" | "CUSTOM_DATE",
    "target_date": "yyyy-MM-dd",
    "target_category": "NAMA_KATEGORI",
    "target_keyword": "SUB_KATEGORI_ATAU_NAMA_BARANG_SPESIFIK"
  },
  "transactions": [
    {
      "amount": 50000,
      "type": "INCOME" | "EXPENSE",
      "debt_type": "DEBT" | "RECEIVABLE",
      "category_id": 15,
      "category_name": "Nama Kategori",
      "is_new_category": false,
      "contact_name": "NAMA_ORANG",
      "clean_note": "Catatan deskripsi ringkas"
    }
  ]
}
""".trimIndent()

        try {
            val realUrl = URI("https", "api.groq.com", "/openai/v1/chat/completions", null).toURL()

            val conn = realUrl.openConnection() as HttpURLConnection
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
                
                val jsonResult = JSONObject(rawResponse)
                val actionType = jsonResult.optString("action_type", "").trim().uppercase(Locale.ROOT)
                val aiResponse = jsonResult.optString("ai_response", "").trim()
                
                // 🔒 BYPASS GERBANG UTAMA: Jika murni VIEW_REPORT, biarkan kepintaran matematika Llama langsung menyembur ke UI chat!
                if (actionType == "VIEW_REPORT" && aiResponse.isNotEmpty()) {
                    return@withContext aiResponse
                }
                
                return@withContext assistant.parseAndExecuteRawAiResponse(rawResponse)
            } else {
                return@withContext "⚠️ Hubungan ke Groq terputus (HTTP ${conn.responseCode})"
            }
        } catch (e: Exception) {
            return@withContext "⚠️ Gangguan Jaringan Lokal: ${e.localizedMessage ?: "Timeout"}"
        }
    }
}
