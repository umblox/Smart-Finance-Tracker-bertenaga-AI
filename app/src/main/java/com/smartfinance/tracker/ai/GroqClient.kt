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
            // HANYA tarik Kategori dan Utang agar AI bisa mencocokkan nama dan membuat kategori baru.
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
            
            // 🚫 DATA TRANSAKSI ($txContext) SENGAJA DIHAPUS DARI SINI AGAR GROQ TIDAK SOK PINTAR BERHITUNG MATEMATIKA!

        } catch (e: Exception) {
            e.printStackTrace()
        }

        val sdfToday = SimpleDateFormat("dd-MM-yyyy (EEEE)", Locale("id", "ID"))
        val todayString = sdfToday.format(Date())

        // 🔥 PROMPT DIKUNCI KAKU: Memaksa Groq melempar VIEW_REPORT ke Kotlin!
        val systemPrompt = """
            Anda adalah Mesin Parser Linguistik (Pemroses Bahasa) untuk aplikasi Smart Finance Tracker milik Ikromul Umam (Mam).
            TUGAS ANDA BUKAN MENGHITUNG ANGKA! Tugas Anda HANYA memetakan maksud kalimat user ke dalam format JSON yang valid.
            
            🗓️ HARI INI: $todayString
            [KATEGORI MASTER]: $catContext
            [DATA UTANG AKTIF]: $debtContext
            
            ATURAN KLASIFIKASI MUTLAK:
            1. VIEW_REPORT (PERMINTAAN LAPORAN): Jika user meminta laporan, riwayat kas, pengeluaran, pemasukan, atau saldo (harian/mingguan/bulanan/tahunan/kategori spesifik), ANDA DILARANG MENJAWAB DENGAN ANGKA! Anda WAJIB mengembalikan action_type: "VIEW_REPORT" dan isi 'report_filter' secara spesifik. Biarkan sistem backend Kotlin yang akan menghitungnya secara matematis!
            2. CHAT_ONLY (NGOBROL BIASA): Jika user menyapa, atau bertanya sisa utang spesifik dari [DATA UTANG AKTIF] di atas, kembalikan action_type: "CHAT_ONLY" dan jawab di 'ai_response' tanpa menyertakan objek 'transactions' sama sekali.
            3. TRANSACTION / DEBT_RECORD / DEBT_PAYMENT (INPUT DATA): Jika user secara tegas MENYURUH mencatat transaksi baru, utang baru, atau cicilan, pilih action_type ini dan isi array 'transactions'. JIKA nama kategori tidak ada di [KATEGORI MASTER], setel "is_new_category": true dan berikan "category_id" di atas angka 200.
            
            FORMAT JSON WAJIB:
            {
              "action_type": "CHAT_ONLY" | "TRANSACTION" | "DEBT_RECORD" | "DEBT_PAYMENT" | "VIEW_REPORT",
              "ai_response": "Teks ramah memanggil 'Mam' (Kosongkan jika action_type adalah VIEW_REPORT)",
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
                   "clean_note": "Catatan",
                   "contact_name": "NAMA_KONTAK",
                   "debt_type": "DEBT" | "RECEIVABLE",
                   "is_new_category": false
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
                put("model", "llama-3.3-70b-versatile") // Model Paling Pintar SPOK
                put("messages", messagesArray)
                put("temperature", 0.0) // Suhu 0 agar robot murni patuh tanpa halusinasi
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
