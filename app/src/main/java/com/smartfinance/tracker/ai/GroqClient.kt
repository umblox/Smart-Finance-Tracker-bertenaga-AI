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
            // 1. Ambil Data Master Kategori
            val categorySnapshot = firestore.collection("categories").get().await()
            for (doc in categorySnapshot.documents) {
                val id = doc.getLong("id") ?: 0L
                val name = doc.getString("name") ?: "Tanpa Nama"
                val type = doc.getString("type") ?: "EXPENSE"
                catContext.append("- ID: $id, Nama: $name, Tipe: $type\n")
            }

            // 2. Ambil Data Catatan Pinjaman Berjalan
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

            // 3. 🔥 OTAK UTAMA LAPORAN: Tarik seluruh riwayat transaksi riil dari Firestore Cloud agar AI bisa berhitung
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

        // 🔥 PERBAIKAN RADIKAL PROMPT SYSTEM: AI dibebaskan berpikir cerdas & dipasok data riil untuk kalkulasi eksplisit
        val finalSystemPrompt = """
Anda adalah asisten keuangan AI premium, sangat cerdas, analitis, dan solutif untuk user bernama Ikromul Umam (Mam).
Tugas utama Anda:
1. Menjawab pertanyaan atau melakukan analisis keuangan komprehensif, eksplisit, terperinci, dan mendalam berdasarkan data transaksi nyata yang dilampirkan di bawah.
2. Jika user meminta instruksi perubahan data (mencatat transaksi/utang baru, mencicil, dll), terjemahkan bahasa natural tersebut menjadi instruksi terstruktur JSON.

🗓️ INFO HARI INI: $todayString

🗂️ DATA MASTER KATEGORI REGISTERED:
${if (catContext.isEmpty()) "- Belum ada kategori" else catContext.toString()}

🤝 DATA CATATAN PINJAMAN BERJALAN SAAT INI:
${if (debtContext.isEmpty()) "- Tidak ada utang/piutang aktif" else debtContext.toString()}

📊 DATA SELURUH RIWAYAT TRANSAKSI RIIL (Gunakan data ini untuk menghitung total laporan, pengeluaran tertinggi, harian, bulanan, tahunan, atau per kategori secara eksplisit):
${if (txContext.isEmpty()) "- Belum ada riwayat mutasi transaksi keuangan" else txContext.toString()}

⚠️ ATURAN ANALISIS DAN BAHASA NATURAL (ANTI-BODOH):
- Jangan kaku! Kata seperti "Beri", "Tolong", "Tampilkan" adalah kata perintah bahasa natural, BUKAN nama orang atau nama kontak terkait. Analisis konteks kalimat secara cerdas.
- Jika user meminta laporan keuangan, performa kas, pengeluaran tertinggi, atau rincian per tanggal/kategori, lakukan kalkulasi matematika secara akurat menggunakan data di atas dan tuliskan rincian detailnya pada kolom "ai_response" dalam bentuk Markdown yang cantik (gunakan bold, bullet points, dan baris baru yang rapi).

STRUKTUR JSON OUTPUT YANG WAJIB ANDA PATUHI:
{
  "action_type": "TRANSACTION" | "DEBT_RECORD" | "DEBT_PAYMENT" | "VIEW_REPORT" | "CHAT_ONLY",
  "ai_response": "Tuliskan jawaban ramah, kasual, laporan keuangan eksplisit mendalam, atau rincian analisis data matematika Anda di sini secara lengkap.",
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

⚠️ MANAGEMENT LOGIK ACTION_TYPE:
- Jika user sekadar meminta laporan, melihat data, bertanya pengeluaran tertinggi, set action_type ke "VIEW_REPORT" atau "CHAT_ONLY" dan berikan rincian hitungan lengkap Anda di kolom ai_response.
- Jika terdeteksi mutasi dana baru, barulah isi objek transactions dengan benar.
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
