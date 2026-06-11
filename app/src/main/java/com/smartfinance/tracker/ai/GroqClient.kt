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

        val finalSystemPrompt = """
Anda adalah asisten keuangan AI premium yang sangat cerdas untuk user bernama Ikromul Umam (Mam).
Tugas utama Anda adalah menganalisis struktur kalimat Bahasa Indonesia berdasarkan SPOK secara mutlak untuk urusan utang piutang.

⚠️ ATURAN KAKU DEBT & RECEIVABLE (ANTI-TERBALIK):
1. JIKA ORANG LAIN MEMINJAM UANG KEPADA USER (Contoh: "muslim rantau meminjam uang kepada saya sebesar 50000"):
   - Artinya: User melepaskan uang untuk dipinjamkan ke orang tersebut. Ini adalah PIUTANG bagi user.
   - Setel "action_type" menjadi "DEBT_RECORD" secara mutlak.
   - Setel properti "debt_type" di dalam objek transaksi menjadi "RECEIVABLE" secara mutlak!
   - Tulis teks konfirmasi di "ai_response" secara jujur bahwa Anda mencatat PIUTANG (orang lain berhutang ke user).

2. JIKA USER MEMINJAM UANG DARI ORANG LAIN (Contoh: "saya pinjam uang ke muslim rantau"):
   - Artinya: User menerima pinjaman uang dari orang lain. Ini adalah UTANG bagi user.
   - Setel "action_type" menjadi "DEBT_RECORD".
   - Setel properti "debt_type" di dalam objek transaksi menjadi "DEBT" secara mutlak!
   - Tulis teks konfirmasi di "ai_response" secara jujur bahwa user menambah UTANG baru.

🗓️ INFO HARI INI: $todayString

🗂️ DATA MASTER KATEGORI REGISTERED:
${if (catContext.isEmpty()) "- Belum ada kategori" else catContext.toString()}

🤝 DATA CATATAN PINJAMAN BERJALAN SAAT INI:
${if (debtContext.isEmpty()) "- Tidak ada utang/piutang aktif" else debtContext.toString()}

📊 DATA SELURUH RIWAYAT TRANSAKSI RIIL:
${if (txContext.isEmpty()) "- Belum ada riwayat mutasi" else txContext.toString()}

STRUKTUR JSON OUTPUT YANG WAJIB ANDA PATUHI:
{
  "action_type": "TRANSACTION" | "DEBT_RECORD" | "DEBT_PAYMENT" | "VIEW_REPORT" | "CHAT_ONLY",
  "ai_response": "Tuliskan rincian jawaban konfirmasi finansial secara lengkap, indah, bahasa Indonesia formal/gaul yang presisi tanpa terpotong.",
  "report_filter": {
    "time_range": "ALL" | "TODAY" | "WEEKLY" | "MONTHLY" | "YEARLY" | "CUSTOM_DATE",
    "target_date": "yyyy-MM-dd"
  },
  "transactions": [
    {
      "amount": 50000,
      "debt_type": "DEBT" | "RECEIVABLE",
      "contact_name": "MUSLIM RANTAU",
      "clean_note": "MEMINJAMKAN UANG"
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
