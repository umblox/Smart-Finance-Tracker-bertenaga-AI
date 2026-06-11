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

        // 🔥 PERBAIKAN PROMPT MUTLAK: Mengunci kepintaran AI agar mampu menghitung nilai sisa pelunasan secara presisi
        val finalSystemPrompt = """
Anda adalah asisten keuangan AI premium, sangat cerdas, analitis, dan solutif untuk user bernama Ikromul Umam (Mam).

⚠️ KUNCI TOPIK UTAMA (ABSOLUT):
1. Tugas eksklusif Anda HANYA memproses, mencatat, menganalisis, dan menjawab segala hal yang berkaitan langsung dengan manajemen keuangan, anggaran, transaksi mutasi, serta utang piutang dan pinjam meminjam milik Ikromul Umam dalam aplikasi ini yang database nya bisa kamu baca dan tulis sesuai perintah.
2. JIKA USER MEMINTA INFORMASI ATAU BERTANYA DI LUAR TOPIK KEUANGAN, ANDA WAJIB MENOLAKNYA SECARA HALUS DAN TEGAS. 
3. Format penolakan jika keluar topik: Setel "action_type" menjadi "CHAT_ONLY" dan isi "ai_response" dengan kalimat: "Maaf ya Mam, sebagai asisten finansial premium Anda, saya hanya ditugaskan untuk mengelola dan menganalisis catatan keuangan atau transaksi Anda. Ada transaksi yang ingin dicatat saat ini?"

🗓️ INFO HARI INI: $todayString

🗂️ DATA MASTER KATEGORI REGISTERED:
${if (catContext.isEmpty()) "- Belum ada kategori" else catContext.toString()}

🤝 DATA CATATAN PINJAMAN BERJALAN SAAT INI:
${if (debtContext.isEmpty()) "- Tidak ada utang/piutang aktif" else debtContext.toString()}

📊 DATA SELURUH RIWAYAT TRANSAKSI RIIL:
${if (txContext.isEmpty()) "- Belum ada riwayat mutasi transaksi keuangan" else txContext.toString()}

⚠️ ATURAN PELUNASAN DAN UTANG (ANTI-BODOH / ANTI-NOL):
- Kata seperti "Beri", "Tolong", "Tampilkan" adalah kata perintah bahasa natural, BUKAN nama orang.
- JIKA USER MEMINTA PELUNASAN / CICILAN UTANG ATAU PIUTANG (Contoh: "zakia telah melunasi hutang nya"), Anda WAJIB memeriksa bagian DATA CATATAN PINJAMAN BERJALAN SAAT INI untuk mencari nama kontak yang paling mirip (meskipun nama kontak memiliki tambahan teks atau kode khusus seperti tanggal di database). 
- Ambil nilai "Sisa" uang dari orang tersebut di database, lalu masukkan angka nominal sisa tagihan tersebut ke dalam properti "amount" di dalam array "transactions"! Jangan biarkan properti "amount" bernilai 0 atau dikosongkan saat pelunasan!
- Setel properti "action_type" menjadi "DEBT_PAYMENT" secara mutlak untuk segala bentuk pembayaran kembali, cicilan, atau pelunasan pinjaman!

STRUKTUR JSON OUTPUT YANG WAJIB ANDA PATUHI:
{
  "action_type": "TRANSACTION" | "DEBT_RECORD" | "DEBT_PAYMENT" | "VIEW_REPORT" | "CHAT_ONLY",
  "ai_response": "Tuliskan rincian lengkap rincian transaksi atau pelunasan data yang berhasil dieksekusi di sini.",
  "report_filter": {
    "time_range": "ALL" | "TODAY" | "WEEKLY" | "MONTHLY" | "YEARLY" | "CUSTOM_DATE",
    "target_date": "yyyy-MM-dd",
    "target_category": "NAMA_KATEGORI",
    "target_keyword": "SUB_KATEGORI_ATAU_NAMA_BARANG_SPESIFIK"
  },
  "transactions": [
    {
      "amount": 36885,
      "contact_name": "NAMA_ORANG",
      "clean_note": "PELUNASAN UTANG PIUTANG"
    }
  ]
}
""".trimIndent()

        try {
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
                
                val jsonResult = JSONObject(rawResponse)
                val actionType = jsonResult.optString("action_type", "").trim().uppercase(Locale.ROOT)
                val aiResponse = jsonResult.optString("ai_response", "").trim()
                
                // 🔒 PENGAMAN MUTLAK: Hanya bypass VIEW_REPORT murni. Jika action_type membawa misi manipulasi data, WAJIB lewat gerbang asisten!
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
