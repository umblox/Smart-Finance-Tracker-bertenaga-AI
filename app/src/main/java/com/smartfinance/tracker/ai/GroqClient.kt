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

        if (apiKey.isEmpty()) return@withContext "⚠️ API Key Groq aman tidak ditemukan dalam sistem build."

        val catContext = java.lang.StringBuilder()
        val myDebtContext = java.lang.StringBuilder()
        val otherReceivableContext = java.lang.StringBuilder()

        try {
            val categorySnapshot = firestore.collection("categories").get().await()
            val allCats = categorySnapshot.documents.mapNotNull { it.data }
            val parents = allCats.filter { it["parentCategoryId"] == null }
            val subs = allCats.filter { it["parentCategoryId"] != null }

            for (p in parents) {
                val pId = p["id"] as? Long ?: 0L
                val pName = p["name"] as? String ?: "Tanpa Nama"
                catContext.append("📁 [INDUK] ID: $pId | Nama: $pName\n")
                val kids = subs.filter { (it["parentCategoryId"] as? Number)?.toLong() == pId }
                for (k in kids) {
                    val kId = k["id"] as? Long ?: 0L
                    val kName = k["name"] as? String ?: "Tanpa Nama"
                    catContext.append("   └── 💰 [SUB-KATEGORI] ID: $kId | Nama: $kName\n")
                }
            }

            val debtSnapshot = firestore.collection("debts").get().await()
            for (doc in debtSnapshot.documents) {
                val isPaid = doc.getBoolean("isPaid") ?: false
                if (!isPaid) {
                    val contactName = doc.getString("contactName") ?: "TEMAN"
                    val remaining = doc.getDouble("remainingAmount") ?: 0.0
                    val type = doc.getString("type") ?: "DEBT"
                    if (type == "DEBT") {
                        myDebtContext.append("- Saya berhutang ke: $contactName | Sisa Belum Dibayar: Rp $remaining\n")
                    } else {
                        otherReceivableContext.append("- $contactName berhutang ke saya | Sisa Belum Ditagih: Rp $remaining\n")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val sdfToday = SimpleDateFormat("dd-MM-yyyy HH:mm (EEEE)", Locale("id", "ID"))
        val todayString = sdfToday.format(Date())

        // 🔥 REVOLUSI WAKTU: Groq dipaksa menghitung start_date & end_date jika Mam minta waktu custom (Kemarin, 2 minggu lalu, dsb)
        val systemPrompt = """
            Anda adalah Asisten Finansial Pribadi untuk aplikasi Smart Finance Tracker milik Ikromul Umam.
            Selalu panggil user dengan sebutan 'Mam'. JANGAN gunakan kata 'Anda'.
            
            🗓️ WAKTU SAAT INI: $todayString
            
            [KATEGORI DATABASE]: 
            $catContext
            
            [HUTANG SAYA (SAYA YANG PINJAM UANG)]:
            ${if (myDebtContext.isEmpty()) "Tidak ada hutang" else myDebtContext.toString()}
            
            [PIUTANG SAYA (ORANG LAIN PINJAM UANG SAYA)]:
            ${if (otherReceivableContext.isEmpty()) "Tidak ada piutang" else otherReceivableContext.toString()}
            
            ATURAN SPOK (DARI, KE, OLEH, KEPADA):
            - Jika Mam menerima uang DARI orang lain (Mam pinjam) -> Kas Mam +, ini DEBT (Hutang Mam).
            - Jika Mam memberikan uang KE/KEPADA orang lain (Mam meminjamkan) -> Kas Mam -, ini RECEIVABLE (Piutang Mam).
            - Jika Mam membayar cicilan KE/KEPADA orang -> DEBT_PAYMENT untuk kontak di [HUTANG SAYA].
            - Jika Mam menerima cicilan DARI/OLEH orang -> DEBT_PAYMENT untuk kontak di [PIUTANG SAYA].
            - JIKA Mam bertanya "Apakah saya punya hutang?", BACA HANYA blok [HUTANG SAYA]. DILARANG menyebut data piutang sebagai hutang!
            
            ATURAN KLASIFIKASI & RESPON:
            1. VIEW_CATEGORIES: Jika Mam meminta daftar kategori. Kosongkan 'ai_response'.
            2. VIEW_REPORT: Jika Mam meminta laporan, pengeluaran tertinggi, atau rincian per kategori. Kosongkan 'ai_response'.
               - Jika Mam meminta waktu spesifik/dinamis (contoh: "kemarin", "minggu lalu", "2 minggu lalu", "3 hari terakhir", "januari sampai maret"), Anda WAJIB menghitung tanggalnya berdasarkan WAKTU SAAT INI. Set "time_range" ke "CUSTOM_RANGE", lalu hitung dan isi "start_date" dan "end_date" dengan format "dd-MM-yyyy". Jika hanya 1 hari ("kemarin"), isi start dan end dengan tanggal yang sama.
            3. CHAT_ONLY: Jika Mam ngobrol atau tanya status hutang spesifik. Jawab dengan ramah di 'ai_response'.
            4. TRANSAKSI/PINJAMAN: Jika mencatat mutasi ("TRANSACTION", "DEBT_RECORD", "DEBT_PAYMENT"). Pastikan SPOK tidak terbalik! Ekstrak tanggal ke 'transaction_date'.
            
            FORMAT JSON WAJIB:
            {
              "action_type": "CHAT_ONLY" | "TRANSACTION" | "DEBT_RECORD" | "DEBT_PAYMENT" | "VIEW_REPORT" | "VIEW_CATEGORIES",
              "ai_response": "Teks balasan asisten yang luwes (Kosongkan jika VIEW_REPORT / VIEW_CATEGORIES)",
              "report_filter": {
                 "report_type": "SUMMARY" | "TOP_EXPENSE" | "CATEGORY_BREAKDOWN",
                 "time_range": "TODAY" | "WEEKLY" | "MONTHLY" | "YEARLY" | "CUSTOM_RANGE" | "ALL",
                 "start_date": "dd-MM-yyyy",
                 "end_date": "dd-MM-yyyy",
                 "target_category": "Nama Kategori",
                 "target_keyword": "Kata Kunci"
              },
              "transactions": [
                 {
                   "amount": 50000,
                   "type": "EXPENSE" | "INCOME",
                   "category_id": 15,
                   "category_name": "Nama Kategori",
                   "clean_note": "Nama barang",
                   "contact_name": "Nama kontak utang",
                   "debt_type": "DEBT" | "RECEIVABLE",
                   "is_new_category": false,
                   "parent_category_id": "ID_Induk_Jika_SubKategori",
                   "transaction_date": "dd-MM-yyyy HH:mm"
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
