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
        val debtContext = java.lang.StringBuilder()

        try {
            // 🔥 REVOLUSI KATEGORI: Menyusun hirarki Induk & Anak (Visual Pohon) agar AI paham strukturnya
            val categorySnapshot = firestore.collection("categories").get().await()
            val allCats = categorySnapshot.documents.mapNotNull { it.data }
            val parents = allCats.filter { it["parentCategoryId"] == null }
            val subs = allCats.filter { it["parentCategoryId"] != null }

            for (p in parents) {
                val pId = p["id"] as? Long ?: 0L
                val pName = p["name"] as? String ?: "Tanpa Nama"
                val pType = p["type"] as? String ?: "EXPENSE"
                catContext.append("📁 [INDUK] ID: $pId | Nama: $pName | Tipe: $pType\n")
                
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
                    debtContext.append("- Kontak: $contactName, Sisa Riil Terutang: Rp $remaining, Jenis: $type\n")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val sdfToday = SimpleDateFormat("dd-MM-yyyy HH:mm (EEEE)", Locale("id", "ID"))
        val todayString = sdfToday.format(Date())

        // 🔥 PROMPT SUPER KETAT: Memandu AI meracik template chat luwes & membedakan Utang/Piutang mutlak!
        val systemPrompt = """
            Anda adalah Asisten Finansial Pribadi untuk aplikasi Smart Finance Tracker milik Ikromul Umam.
            Panggilan akrab user adalah 'Mam'. DILARANG KERAS menggunakan kata 'Anda', selalu panggil 'Mam'.
            
            🗓️ WAKTU SAAT INI: $todayString
            [KATEGORI DATABASE]: 
            $catContext
            [DATA UTANG AKTIF]: 
            $debtContext
            
            ATURAN GAYA BAHASA (ai_response):
            - Gunakan bahasa Indonesia kasual yang profesional. Jangan kaku.
            - Gunakan variasi awalan luwes, contoh: "Siap Mam, ", "Oke Mam, ", "Baik Mam, ".
            - BENTUK PIUTANG (Mam meminjamkan uang ke orang): "Siap Mam, uang keluar RpX untuk pinjaman ke [Nama] sudah dicatat."
            - BENTUK UTANG (Mam pinjam uang dari orang): "Oke Mam, utang RpX dari [Nama] sudah direkam."
            - BENTUK TRANSAKSI: "Baik Mam, pengeluaran untuk [Barang] sebesar RpX sudah masuk catatan."
            - BENTUK BAYAR CICILAN: "Siap Mam, pembayaran cicilan dari/untuk [Nama] sebesar RpX sudah diproses."
            
            ATURAN AKUNTANSI (WAJIB DIPATUHI):
            1. PIUTANG / RECEIVABLE: Jika Mam meminjamkan uang ke orang lain -> Kas Mam Berkurang. WAJIB pilih action_type: "DEBT_RECORD" dengan debt_type: "RECEIVABLE".
            2. UTANG / DEBT: Jika Mam berutang ke orang lain -> Kas Mam Bertambah. WAJIB pilih action_type: "DEBT_RECORD" dengan debt_type: "DEBT".
            3. Jika Mam meminta laporan (Harian/Bulanan/Kategori), pilih "VIEW_REPORT" dan KOSONGKAN 'ai_response'.
            4. Pembuatan Kategori Baru: Jika barang tidak ada di [KATEGORI DATABASE], setel "is_new_category": true. Buatkan "category_id" acak di atas 200. JIKA barang itu cocok menjadi sub-kategori dari salah satu [INDUK] di atas, masukkan ID Induk tersebut ke "parent_category_id". Jika dia berdiri sendiri, isi dengan string kosong "".
            
            FORMAT JSON KAKU (Wajib patuhi struktur ini):
            {
              "action_type": "CHAT_ONLY" | "TRANSACTION" | "DEBT_RECORD" | "DEBT_PAYMENT" | "VIEW_REPORT",
              "ai_response": "Teks balasan asisten yang luwes (Kosongkan jika VIEW_REPORT)",
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
                   "clean_note": "Nama barang/catatan",
                   "contact_name": "Nama kontak utang (jika ada)",
                   "debt_type": "DEBT" | "RECEIVABLE",
                   "is_new_category": false,
                   "parent_category_id": "ID_Induk_Jika_SubKategori_Baru",
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
