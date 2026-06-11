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
        val txContext = java.lang.StringBuilder()

        try {
            val categorySnapshot = firestore.collection("categories").get().await()
            for (doc in categorySnapshot.documents) {
                val id = doc.getLong("id") ?: 0L
                val name = doc.getString("name") ?: "Tanpa Nama"
                val type = doc.getString("type") ?: "EXPENSE"
                catContext.append("- ID: $id, Nama: $name, Tipe Aliran: $type\n")
            }

            val debtSnapshot = firestore.collection("debts").get().await()
            for (doc in debtSnapshot.documents) {
                val contactName = doc.getString("contactName") ?: "TEMAN"
                val amount = doc.getDouble("amount") ?: 0.0
                val remaining = doc.getDouble("remainingAmount") ?: 0.0
                val type = doc.getString("type") ?: "DEBT"
                val isPaid = doc.getBoolean("isPaid") ?: false
                if (!isPaid) {
                    debtContext.append("- Kontak: $contactName, Total Hutang Awal: $amount, Sisa Riil Terutang: $remaining, Jenis: $type\n")
                }
            }

            val txSnapshot = firestore.collection("transactions").get().await()
            val sortedDocs = txSnapshot.documents.sortedByDescending { doc -> doc.getLong("timestamp") ?: 0L }.take(15)
            val sdf = SimpleDateFormat("dd-MM-yyyy • HH:mm", Locale("id", "ID"))
            for (doc in sortedDocs) {
                val amt = doc.getDouble("amount") ?: 0.0
                val type = doc.getString("type") ?: "EXPENSE"
                val catName = doc.getString("categoryName") ?: "Umum"
                val note = doc.getString("note") ?: "Transaksi AI"
                val ts = doc.getLong("timestamp") ?: System.currentTimeMillis()
                txContext.append("- [${sdf.format(Date(ts))}] $note | Kategori: $catName | Aliran: $type | Nominal: $amt\n")
            }

            // ✅ FIX PROMPT: Ditambahkan instruksi tegas 'RETURN ONLY JSON' di awal kalimat agar mematuhi aturan strict response_format Groq
            val systemPrompt = """
                Anda adalah sistem kecerdasan buatan terpusat premium dari aplikasi Smart Finance Tracker milik Mam Ikromul Umam.
                Anda WAJIB selalu merespons dalam format JSON yang valid.
                
                Berikut adalah data riil database terenkripsi server cloud saat ini:
                
                [DATA MASTER KATEGORI YANG SAH]
                $catContext
                
                [DATA BUKU UTANG PIUTANG AKTIF]
                $debtContext
                
                [15 TRANSAKASI MUTASI KAS TERKINI]
                $txContext
                
                Aturan Mutlak Klasifikasi JSON (Wajib Patuhi Tanpa Toleransi):
                1. Jika user hanya mengobrol, menyapa, bertanya tentang status keuangan, mengonfirmasi data utang, atau bertanya 'apakah saya punya hutang?', Anda WAJIB mengembalikan status action_type: "CHAT_ONLY". Jawablah pertanyaan mereka secara ramah, detail, sopan, dan panggil dengan sebutan 'Mam'. Isikan jawaban Anda pada kolom 'ai_response'. DILARANG SEKALIPUN memasukkan array 'transactions' pada kondisi CHAT_ONLY ini agar tidak memicu transaksi ghaib!
                2. Jika user meminta ringkasan laporan kas keuangan, total pengeluaran, total pemasukan, baik harian, mingguan, bulanan atau tahunan, Anda WAJIB mengembalikan action_type: "VIEW_REPORT". Buat filter yang tepat pada objek 'report_filter'.
                3. Jika user memberikan kalimat perintah tegas untuk mencatat mutasi pengeluaran/pemasukan baru, mencatat utang baru, atau mencatat cicilan/pembayaran utang, barulah Anda mengembalikan action_type sesuai perintahnya ("TRANSACTION" / "DEBT_RECORD" / "DEBT_PAYMENT") CODED JSON beserta array 'transactions'.
                
                Format JSON yang wajib Anda kembalikan harus selalu bersih terstruktur seperti ini:
                {
                  "action_type": "CHAT_ONLY" atau "TRANSACTION" atau "DEBT_RECORD" atau "DEBT_PAYMENT" atau "VIEW_REPORT",
                  "ai_response": "Teks balasan obrolan biasa atau teks konfirmasi di sini",
                  "report_filter": {
                     "time_range": "TODAY" atau "WEEKLY" atau "MONTHLY" or "YEARLY" atau "CUSTOM_DATE" atau "ALL",
                     "target_date": "yyyy-MM-dd" (diisi hanya jika memakai CUSTOM_DATE),
                     "target_category": "Nama kategori spesifik jika ada filter",
                     "target_keyword": "Kata kunci barang spesifik jika ada filter"
                  },
                  "transactions": [
                     {
                       "amount": 15000,
                       "type": "EXPENSE" atau "INCOME",
                       "category_id": 15,
                       "category_name": "Nama Kategori",
                       "clean_note": "Catatan ringkas",
                       "contact_name": "Nama orang jika berhubungan dengan utang",
                       "debt_type": "DEBT" atau "RECEIVABLE",
                       "is_new_category": false
                     }
                  ]
                }
            """.trimIndent()

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
                put("model", "llama3-70b-8192")
                put("messages", messagesArray)
                put("temperature", 0.1)
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
                // ✅ LEBIH DETAIL: Jika gagal, baca pesan error internal dari server Groq agar mudah ditelusuri
                val errorReader = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream))
                val errorText = errorReader.readText()
                return@withContext "⚠️ Hubungan ke Groq terputus (HTTP ${conn.responseCode}): $errorText"
            }
        } catch (e: Exception) {
            return@withContext "⚠️ Gangguan Jaringan Lokal: ${e.localizedMessage ?: "Timeout"}"
        }
    }
}
