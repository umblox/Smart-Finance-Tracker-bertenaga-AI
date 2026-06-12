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
            // 1. Ambil Seluruh Data Master Kategori Terdaftar
            val categorySnapshot = firestore.collection("categories").get().await()
            for (doc in categorySnapshot.documents) {
                val id = doc.getLong("id") ?: 0L
                val name = doc.getString("name") ?: "Tanpa Nama"
                val type = doc.getString("type") ?: "EXPENSE"
                catContext.append("- ID: $id, Nama: $name, Tipe Aliran: $type\n")
            }

            // 2. Ambil Seluruh Data Catatan Pinjaman Berjalan yang Belum Lunas
            val debtSnapshot = firestore.collection("debts").get().await()
            for (doc in debtSnapshot.documents) {
                val contactName = doc.getString("contactName") ?: "TEMAN"
                val amount = doc.getDouble("amount") ?: 0.0
                val remaining = doc.getDouble("remainingAmount") ?: 0.0
                val type = doc.getString("type") ?: "DEBT"
                val isPaid = doc.getBoolean("isPaid") ?: false
                if (!isPaid) {
                    debtContext.append("- Kontak: $contactName, Total Hutang Awal: Rp $amount, Sisa Riil Terutang: Rp $remaining, Jenis: $type\n")
                }
            }

            // 3. ✅ SINKRONISASI TOTAL: Melepas pembatasan .take(15) agar AI membaca seluruh riwayat mutasi tanpa amnesia
            val txSnapshot = firestore.collection("transactions").get().await()
            val allDocs = txSnapshot.documents.sortedByDescending { doc -> doc.getLong("timestamp") ?: 0L }
            
            // Menggunakan format premium terpadu aplikasi
            val sdf = SimpleDateFormat("dd-MM-yyyy • HH:mm", Locale("id", "ID"))
            for (doc in allDocs) {
                val amt = doc.getDouble("amount") ?: 0.0
                val type = doc.getString("type") ?: "EXPENSE"
                val catName = doc.getString("categoryName") ?: "Umum"
                val note = doc.getString("note") ?: "Transaksi AI"
                val ts = doc.getLong("timestamp") ?: System.currentTimeMillis()
                txContext.append("- [${sdf.format(Date(ts))}] $note | Kategori: $catName | Aliran: $type | Nominal: Rp $amt\n")
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Tampilkan acuan waktu hari ini dengan format premium yang tepat
        val sdfToday = SimpleDateFormat("dd-MM-yyyy (EEEE)", Locale("id", "ID"))
        val todayString = sdfToday.format(Date())

        val systemPrompt = """
            Anda adalah sistem kecerdasan buatan terpusat premium dari aplikasi Smart Finance Tracker milik Mam Ikromul Umam (Mam).
            Anda WAJIB memberikan respons dalam format JSON objek yang valid.
            
            🗓️ INFO WAKTU ACUAN HARI INI: $todayString
            
            [DATA MASTER KATEGORI REGISTERED DI DATABASE]
            $catContext
            
            [DATA BUKU UTANG PIUTANG AKTIF DI DATABASE]
            $debtContext
            
            [DATA SELURUH RIWAYAT MUTASI TRANSAKSI KEUANGAN KAS]
            $txContext
            
            Aturan Klasifikasi Maksud User (Analisis Hubungan SPOK Indonesia Secara Ketat):
            1. CHAT_ONLY (Hanya Mengobrol / Bertanya Data / Konfirmasi): Jika user menyapa, bertanya status keuangan, mengonfirmasi data utang, atau bertanya seperti 'apakah saya punya hutang?', Anda WAJIB mengembalikan action_type: "CHAT_ONLY". Jawablah pertanyaan mereka secara akurat berdasarkan data di atas dengan ramah, sopan, detail, dan panggil dengan sebutan 'Mam' di dalam kolom 'ai_response'. DILARANG SEKALIPUN menyertakan objek data pada array 'transactions' pada kondisi CHAT_ONLY ini agar tidak memicu transaksi ghaib!
            2. VIEW_REPORT (Permintaan Laporan Finansial): Jika user meminta laporan keuangan, total pengeluaran, ringkasan kas, atau akumulasi nominal (baik harian, mingguan, bulanan, tahunan, per kategori, atau per keyword barang), Anda WAJIB mengembalikan action_type: "VIEW_REPORT". Anda WAJIB menyusun filter yang tepat pada objek 'report_filter' menggunakan format penanggalan premium 'dd-MM-yyyy'.
            3. TRANSAKSI MANUAL MANUAL & PINJAMAN: Jika user memberikan kalimat perintah tegas untuk mencatat mutasi pengeluaran/pemasukan baru, mencatat utang baru, atau mencatat cicilan pembayaran utang, kembalikan action_type yang sesuai ("TRANSACTION" / "DEBT_RECORD" / "DEBT_PAYMENT") beserta array 'transactions'. Analisis struktur kalimat SPOK secara jeli agar subjek pembayar dan penerima dana tidak terbalik!
            
            Format response wajib berupa JSON objek murni tanpa markdown pembungkus di luar objeknya:
            {
              "action_type": "CHAT_ONLY" atau "TRANSACTION" atau "DEBT_RECORD" atau "DEBT_PAYMENT" atau "VIEW_REPORT",
              "ai_response": "Teks jawaban obrolan atau teks konfirmasi transaksi di sini",
              "report_filter": {
                 "time_range": "TODAY" atau "WEEKLY" atau "MONTHLY" atau "YEARLY" atau "CUSTOM_DATE" atau "ALL",
                 "target_date": "dd-MM-yyyy",
                 "target_category": "Nama kategori jika ada filter",
                 "target_keyword": "Kata kunci nama barang spesifik jika ada filter"
              },
              "transactions": [
                 {
                   "amount": 10000,
                   "type": "EXPENSE" atau "INCOME",
                   "category_id": 15,
                   "category_name": "Nama Kategori",
                   "clean_note": "Catatan deskripsi barang",
                   "contact_name": "Nama orang jika berhubungan dengan utang/piutang",
                   "debt_type": "DEBT" atau "RECEIVABLE",
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
                put("model", "llama-3.3-70b-versatile")
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
                val errorReader = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream))
                val errorText = errorReader.readText()
                return@withContext "⚠️ Hubungan ke Groq terputus (HTTP ${conn.responseCode}): $errorText"
            }
        } catch (e: Exception) {
            return@withContext "⚠️ Gangguan Jaringan Lokal: ${e.localizedMessage ?: "Timeout"}"
        }
    }
}
