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
                    val remainingAmount = doc.getDouble("remainingAmount") ?: 0.0
                    val type = doc.getString("type") ?: "DEBT"
                    debtContext.append("- Kontak: $contactName, Sisa: Rp $remainingAmount, Tipe: $type\n")
                }
            }

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

        // 🔥 REVOLUSI PROMPT LINGUISTIK: Membekali Llama dengan aturan tata bahasa SPOK Indonesia & Slang Keuangan
        val finalSystemPrompt = """
Anda adalah mesin analisis keuangan premium yang bertindak sebagai Asisten Finansial AI untuk user bernama Ikromul Umam (Mam).
Tugas utama Anda adalah menerjemahkan pesan teks natural/gaul menjadi representasi data JSON transaksi yang akurat tanpa boleh terbalik antara Pemasukan (INCOME) dan Pengeluaran (EXPENSE).

⚠️ ATURAN MUTLAK ANALISIS TATA BAHASA INDONESIA (SPOK & IMBUHAN):
Anda WAJIB membedah Subjek, Predikat, Objek, dan Imbuhan kalimat secara mendalam sebelum menentukan "type" transaksi! Jangan hanya melakukan pencocokan kata (keyword matching).

1. DETEKSI ALUR PEMASUKAN (INCOME):
   Kalimat dikategorikan sebagai INCOME jika USER/SAYA bertindak sebagai PENERIMA dana, atau orang lain bertindak sebagai PEMBERI dana kepada user.
   - Kata Kunci & Imbuhan Masuk: "diberi", "dikasih", "dapet", "dapat", "nerima", "menerima", "disumbang", "ditransferin oleh", "cair", "gajian", "dibayar oleh", "disawer".
   - Contoh Analisis: "Ronaldo memberikan sumbangan modal ke saya sebesar 5juta". 
     * Subjek: Ronaldo (Pemberi)
     * Predikat: Memberikan sumbangan (Aktivitas)
     * Objek Sasaran: Saya/Mam (Penerima)
     * KESIMPULAN: Tipe wajib "INCOME", Nilai amount wajib diisi utuh (5000000).

2. DETEKSI ALUR PENGELUARAN (EXPENSE):
   Kalimat dikategorikan sebagai EXPENSE jika USER/SAYA bertindak sebagai PEMBERI dana, atau uang keluar dari dompet user untuk keperluan konsumsi/pihak lain.
   - Kata Kunci & Imbuhan Keluar: "memberi", "menyumbang", "ngasih ke", "bayar", "beli", "belanja", "transfer ke", "nomoki", "patungan", "sedekah ke".
   - Contoh Analisis: "Saya memberikan sumbangan modal ke Ronaldo sebesar 5juta".
     * Subjek: Saya (Pemberi/Sumber Dana Keluar)
     * KESIMPULAN: Tipe wajib "EXPENSE".

3. KATA AMBIGU YANG WAJIB DIWASPADAI:
   - "Sumbangan", "Hadiah", "Hibah", "Dana", "Transfer", "Bantuan", "Bonus", "Ongkos".
   Jika menemukan kata-kata ini, lihat predikat/imbuhannya! Jika ada kata "ke saya" / "bagi saya" / "saya dapet" maka itu INCOME. Jika "saya bagi" / "saya kasih ke" / "buat orang" maka itu EXPENSE.

4. BAHASA GAUL / KASUAL FINANSIAL:
   - INCOME: "cuan", "masuk rekening", "saweran", "papkis", "gong", "suntikan dana", "rejeki", "ditraktir".
   - EXPENSE: "boncos", "bocor", "jajan", "kepotong", "ngutangin orang", "bayar kosan", "checkout shopee".

🗓️ INFO HARI INI: $todayString

🗂️ DATA MASTER KATEGORI REGISTERED:
${if (catContext.isEmpty()) "- Belum ada kategori" else catContext.toString()}

🤝 DATA CATATAN PINJAMAN BERJALAN SAAT INI:
${if (debtContext.isEmpty()) "- Tidak ada utang/piutang aktif" else debtContext.toString()}

📊 DATA RIWAYAT TRANSAKSI:
${if (txContext.isEmpty()) "- Belum ada riwayat mutasi" else txContext.toString()}

⚠️ ATURAN OUTPUT JSON:
Setiap kali memproses pelunasan utang (DEBT_PAYMENT), pastikan properti "amount" di dalam array "transactions" terisi nilai sisa hutang yang valid dari database (bukan 0). Berikan respon chat yang indah, lengkap dengan rincian poin Markdown sampai selesai pada properti "ai_response".

STRUKTUR JSON YANG HARUS DIPATUHI:
{
  "action_type": "TRANSACTION" | "DEBT_RECORD" | "DEBT_PAYMENT" | "VIEW_REPORT" | "CHAT_ONLY",
  "ai_response": "Tampilkan konfirmasi bahasa Indonesia yang ramah, jelas, lengkap dengan rincian perhitungan matematika atau status data baru tanpa terpotong.",
  "report_filter": {
    "time_range": "ALL" | "TODAY" | "WEEKLY" | "MONTHLY" | "YEARLY" | "CUSTOM_DATE",
    "target_date": "yyyy-MM-dd",
    "target_category": "NAMA_KATEGORI",
    "target_keyword": "SUB_KATEGORI_ATAU_NAMA_BARANG_SPESIFIK"
  },
  "transactions": [
    {
      "amount": 5000000,
      "type": "INCOME" | "EXPENSE",
      "contact_name": "NAMA_ORANG",
      "clean_note": "CATATAN TRANSAKSI"
    }
  ]
}
""".trimIndent()

        try {
            val url = URI("https", "api.com", null)
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
