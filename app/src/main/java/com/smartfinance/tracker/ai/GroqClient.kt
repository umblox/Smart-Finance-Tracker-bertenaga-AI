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
        val prefs = context.getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        
        var apiKey = prefs.getString("groq_key_override", "") ?: ""
        if (apiKey.isEmpty()) apiKey = BuildConfig.GROQ_API_KEY
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
                val pType = p["type"] as? String ?: "EXPENSE"
                catContext.append("📁 [INDUK - $pType] ID: $pId | Nama: $pName\n")
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
                val note = doc.getString("note") ?: "Transaksi"
                val ts = doc.getLong("timestamp") ?: System.currentTimeMillis()
                txContext.append("- [${sdfTx.format(Date(ts))}] $note | Kategori: $catName | Tipe: $type | Nominal: Rp$amt\n")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val sdfToday = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale("id", "ID"))
        val todayString = sdfToday.format(Date())

        // 🔥 PROMPT SUPERIOR: DILENGKAPI KEKUATAN MEMBUAT KATEGORI
        val defaultPrompt = """
            Anda adalah Asisten Finansial cerdas untuk Ikromul Umam (Mam).
            WAKTU SAAT INI: {TODAY_DATE}
            
            [DATABASE KATEGORI]: \n{CAT_CONTEXT}
            [HUTANG SAYA (SAYA PINJAM)]: \n{MY_DEBT_CONTEXT}
            [PIUTANG SAYA (ORANG PINJAM)]: \n{OTHER_RECEIVABLE_CONTEXT}
            
            ATURAN (MUTLAK):
            1. PENGELUARAN & PEMASUKAN: Pemasukan -> "INCOME". Pengeluaran -> "EXPENSE".
            2. TANGGAL KUSTOM: "kemarin" hitung mundur 1 hari. Format harus "dd-MM-yyyy HH:mm".
            3. KONFIRMASI CERDAS: Jika jelas, LANGSUNG EKSEKUSI. Tunda (pending_transaction) hanya jika sangat aneh.
            4. UTANG/PIUTANG: Mam pinjam uang DARI orang -> DEBT. Mam minjemin uang KE orang -> RECEIVABLE.
            5. BUAT KATEGORI BARU: Jika Mam menyuruh membuat kategori/sub-kategori, gunakan action_type "CREATE_CATEGORY". Jika dia minta membuat sub-kategori, WAJIB cari ID Induk dari [DATABASE KATEGORI] dan masukkan ke 'parent_category_id' (jika tidak ketemu, set "").
               
            PERINGATAN: Buatlah kalimat luwes Anda sendiri di dalam 'ai_response'. DILARANG MENGCOPY TEMPLATE JSON INI!
            
            FORMAT JSON WAJIB:
            {
              "action_type": "CHAT_ONLY" | "TRANSACTION" | "DEBT_RECORD" | "DEBT_PAYMENT" | "VIEW_REPORT" | "VIEW_CATEGORIES" | "CREATE_CATEGORY",
              "ai_response": "Tulis jawaban natural Anda di sini...",
              "pending_transaction": null,
              "report_filter": null,
              "new_category": {
                 "name": "Nama Kategori",
                 "type": "INCOME" | "EXPENSE" | "DEBT" | "RECEIVABLE",
                 "parent_category_id": "ID_Induk_Jika_Ada"
              },
              "transactions": [
                 {
                   "amount": 0, "type": "EXPENSE", "category_id": 1, "category_name": "Nama Kategori", "clean_note": "Catatan", "contact_name": "", "debt_type": "DEBT", "is_new_category": false, "transaction_date": "dd-MM-yyyy HH:mm"
                 }
              ]
            }
        """.trimIndent()

        var finalSystemPrompt = prefs.getString("expert_system_prompt", defaultPrompt) ?: defaultPrompt
        if (finalSystemPrompt.contains("{TODAY_DATE}")) {
            finalSystemPrompt = finalSystemPrompt.replace("{TODAY_DATE}", todayString)
                .replace("{CAT_CONTEXT}", catContext.toString())
                .replace("{MY_DEBT_CONTEXT}", if (myDebtContext.isEmpty()) "Bersih" else myDebtContext.toString())
                .replace("{OTHER_RECEIVABLE_CONTEXT}", if (otherReceivableContext.isEmpty()) "Bersih" else otherReceivableContext.toString())
                .replace("{TX_CONTEXT}", txContext.toString())
        }

        try {
            val url = URI("https://api.groq.com/openai/v1/chat/completions").toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.doOutput = true

            val messagesArray = JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", finalSystemPrompt) })
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
