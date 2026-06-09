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

            // 3. Tarik seluruh riwayat transaksi riil dari Firestore Cloud
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

        // 🔥 PERBAIKAN TOTAL PROMPT: Paksa AI menyusun laporan eksplisit terperinci di kolom 'ai_response'
        val finalSystemPrompt = """
Anda adalah asisten keuangan AI premium, sangat cerdas, analitis, dan solutif untuk user bernama Ikromul Umam (Mam).
Tugas utama Anda:
1. Menjawab pertanyaan atau melakukan analisis keuangan komprehensif, eksplisit, terperinci, mendalam, dan menghitung total nominal uang secara matematis berdasarkan data mutasi nyata yang dilampirkan di bawah.
2. Jika user meminta instruksi perubahan data (mencatat transaksi/utang baru, mencicil, dll), terjemahkan bahasa natural tersebut menjadi instruksi terstruktur JSON.

🗓️ INFO HARI INI: $todayString

🗂️ DATA MASTER KATEGORI REGISTERED:
${if (catContext.isEmpty()) "- Belum ada kategori" else catContext.toString()}

🤝 DATA CATATAN PINJAMAN BERJALAN SAAT INI:
${if (debtContext.isEmpty()) "- Tidak ada utang/piutang aktif" else debtContext.toString()}

📊 DATA SELURUH RIWAYAT TRANSAKSI RIIL (WAJIB hitung total laporan, pengeluaran tertinggi, rincian harian, bulanan, tahunan, atau per kategori dari sini):
${if (txContext.isEmpty()) "- Belum ada riwayat mutasi transaksi keuangan" else txContext.toString()}

⚠️ ATURAN ANALISIS DAN LAPORAN (ANTI-NOL / ANTI-BODOH):
- Kata seperti "Beri", "Tolong", "Tampilkan" adalah kata perintah bahasa natural, BUKAN nama orang. Analisis konteks kalimat secara cerdas.
- JIKA USER MEMINTA LAPORAN KEUANGAN (Harian, Bulanan, Tahunan, Tertinggi, Per Kategori, atau barang spesifik), Anda WAJIB berhitung menggunakan matematika yang akurat berdasarkan data mutasi riil di atas. Tuliskan rincian hitungan, persentase, atau detailnya secara transparan dan terperinci langsung di kolom "ai_response" menggunakan format Markdown yang rapi (bold, bullet points, baris baru). Jangan biarkan kosong atau bernilai 0 jika datanya ada di atas!

STRUKTUR JSON OUTPUT YANG WAJIB ANDA PATUHI:
{
  "action_type": "TRANSACTION" | "DEBT_RECORD" | "DEBT_PAYMENT" | "VIEW_REPORT" | "CHAT_ONLY",
  "ai_response": "Tuliskan hasil laporan keuangan eksplisit mendalam, hitungan matematika Anda, atau balasan chat di sini secara lengkap.",
  "report_filter": {
    "time_range": "ALL" | "TODAY" | "WEEKLY" | "MONTHLY" | "YEARLY" | "CUSTOM_DATE",
    "target_date": "yyyy-MM-dd",
    "target_category": "NAMA_KATEGORI",
    "target_keyword": "SUB_KATEGORI_ATAU_NAMA_BARANG_SPESIFIK"
  },
  "transactions": [
    {
      "amount": 50000,
      "contact_name": "NAMA_ORANG",
      "clean_note": "Catatan deskripsi ringkas"
    }
  ]
}

⚠️ MANAGEMENT LOGIK ACTION_TYPE & FILTER DINAMIS:
- Jika user meminta laporan, melihat kas, mencari tahu pengeluaran tertinggi, set action_type ke "VIEW_REPORT" dan berikan rincian hitungan lengkap Anda di kolom ai_response.
- Jika user bertanya tentang barang, sub-kategori, atau aktivitas spesifik (contoh: "Pertamax", "Ganti Oli", "Nasi Goreng"), masukkan nama barang/aktivitas tersebut ke dalam parameter "target_keyword" secara dinamis dalam bentuk huruf besar (UPPERCASE). Jika user hanya bertanya kategori secara umum, kosongkan parameter "target_keyword" dengan string "".
""".trimIndent()

        try {
            val uri = URI("https", "api.com", null) // Dummy mapping bypass
            val url = URI("https", "api.groq.com", "/openai/v1/chat/completions", null).toURL()

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
                
                // 🔥 SINKRONISASI BYPASS: Ambil objek JSON respons murni
                val jsonResult = JSONObject(rawResponse)
                val actionType = jsonResult.optString("action_type", "").trim().uppercase(Locale.ROOT)
                val aiResponse = jsonResult.optString("ai_response", "").trim()
                
                // Jika tipenya meminta laporan, langsung bypass keluarkan hitungan Markdown eksplisit milik Llama!
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
