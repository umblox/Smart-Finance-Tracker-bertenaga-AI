package com.smartfinance.tracker.ai

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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
        val txContext = java.lang.StringBuilder()

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
                    if (type == "DEBT") myDebtContext.append("- Saya berhutang ke: $contactName | Sisa: Rp $remaining\n")
                    else otherReceivableContext.append("- $contactName berhutang ke saya | Sisa: Rp $remaining\n")
                }
            }

            val txSnapshot = firestore.collection("transactions").orderBy("timestamp", Query.Direction.DESCENDING).limit(50).get().await()
            val sdfTx = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale("id", "ID"))
            for (doc in txSnapshot.documents) {
                val amt = doc.getDouble("amount") ?: 0.0
                val type = doc.getString("type") ?: "EXPENSE"
                val catName = doc.getString("categoryName") ?: "Umum"
                val note = doc.getString("note") ?: "Transaksi AI"
                val ts = doc.getLong("timestamp") ?: System.currentTimeMillis()
                txContext.append("- [${sdfTx.format(Date(ts))}] $note | Kategori: $catName | Tipe: $type | Nominal: Rp$amt\n")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val sdfToday = SimpleDateFormat("dd-MM-yyyy HH:mm (EEEE)", Locale("id", "ID"))
        val todayString = sdfToday.format(Date())

        val systemPrompt = """
            Anda adalah Asisten Finansial Pribadi cerdas untuk Ikromul Umam (selalu panggil 'Mam').
            🗓️ WAKTU SAAT INI: $todayString
            
            [KATEGORI DATABASE]: \n$catContext
            [HUTANG SAYA]: \n${if (myDebtContext.isEmpty()) "Tidak ada" else myDebtContext.toString()}
            [PIUTANG SAYA]: \n${if (otherReceivableContext.isEmpty()) "Tidak ada" else otherReceivableContext.toString()}
            [50 TRANSAKSI TERAKHIR MAM]: \n${if (txContext.isEmpty()) "Belum ada riwayat" else txContext.toString()}
            (GUNAKAN DATA TRANSAKSI DI ATAS HANYA UNTUK MENCARI TANGGAL/WAKTU. DILARANG MENGHITUNG TOTAL DARI SINI!)
            
            ATURAN INTERAKSI & KLASIFIKASI MUTLAK:
            1. PERTANYAAN TANGGAL ("Kapan saya beli X?"):
               - Cari barang di [50 TRANSAKSI TERAKHIR MAM]. Kembalikan "CHAT_ONLY" dan jawab tanggalnya.
            
            2. PERMINTAAN LAPORAN (VIEW_REPORT):
               - Jika Mam meminta "rincian", "detail", atau "daftar" pembelian suatu barang (misal: "Rincian beli rokok", "Detail pengeluaran bensin"), WAJIB set "report_type" ke "ITEM_DETAILS".
               - Jika Mam menyebut TANGGAL ATAU WAKTU SPESIFIK (contoh: "12 juni", "kemarin", "3 hari lalu", "tanggal 1 sampai 5"), HARAM HUKUMNYA menggunakan TODAY/MONTHLY. Anda WAJIB memakai "CUSTOM_RANGE", lalu hitung presisi dan isi "start_date" dan "end_date" ("dd-MM-yyyy").
            
            3. CATAT TRANSAKSI & KONFIRMASI 2 OPSI:
               - Jika barang sangat sesuai kategori yang ada, catat ("TRANSACTION").
               - JIKA BARANG BARU/MERAGUKAN: TUNDA PENCATATAN! Kembalikan "CHAT_ONLY" dan tanyakan: "Mam, untuk transaksi [Barang] Rp[Nominal], mau dibuatkan kategori/sub baru, atau gabung ke kategori terdekat [Sebutkan]?"
            
            FORMAT JSON WAJIB:
            {
              "action_type": "CHAT_ONLY" | "TRANSACTION" | "DEBT_RECORD" | "DEBT_PAYMENT" | "VIEW_REPORT" | "VIEW_CATEGORIES",
              "ai_response": "Balasan luwes (Kosongkan jika VIEW_REPORT / VIEW_CATEGORIES)",
              "report_filter": {
                 "report_type": "SUMMARY" | "TOP_EXPENSE" | "CATEGORY_BREAKDOWN" | "ITEM_DETAILS",
                 "time_range": "TODAY" | "WEEKLY" | "MONTHLY" | "LAST_MONTH" | "YEARLY" | "CUSTOM_RANGE" | "ALL",
                 "start_date": "dd-MM-yyyy",
                 "end_date": "dd-MM-yyyy",
                 "target_category": "NAMA_KATEGORI_JIKA_ADA",
                 "target_keyword": "KATA_KUNCI_JIKA_ADA"
              },
              "transactions": [{
                 "amount": 50000, "type": "EXPENSE" | "INCOME", "category_id": 15, "category_name": "Nama", 
                 "clean_note": "Barang", "contact_name": "Kontak", "debt_type": "DEBT" | "RECEIVABLE", 
                 "is_new_category": false, "parent_category_id": "ID_Induk_Jika_Sub", "transaction_date": "dd-MM-yyyy HH:mm"
              }]
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
