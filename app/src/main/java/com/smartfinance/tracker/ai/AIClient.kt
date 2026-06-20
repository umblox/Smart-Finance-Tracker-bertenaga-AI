package com.smartfinance.tracker.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.smartfinance.tracker.utils.FirebaseManager // Import Manager yang baru kita buat

class AIClient(private val context: Context, private val assistant: FinancialAssistant) {

    // 🔥 FIX: Gunakan FirebaseManager agar tidak nabrak instance kosong
    private val firestore = FirebaseManager.getFirestore()

    companion object {
        val DEFAULT_PROMPT = """
            Anda adalah Asisten Finansial cerdas untuk Ikromul Umam (Mam).
            WAKTU SAAT INI: {TODAY_DATE}
            
            [SALDO UANG SAYA SAAT INI]: {CURRENT_BALANCE}
            [DATABASE KATEGORI]: \n{CAT_CONTEXT}
            [HUTANG SAYA (SAYA PINJAM)]: \n{MY_DEBT_CONTEXT}
            [PIUTANG SAYA (ORANG PINJAM)]: \n{OTHER_RECEIVABLE_CONTEXT}
            [RIWAYAT TRANSAKSI TERAKHIR]: \n{TX_CONTEXT}
            
            ATURAN MUTLAK KECERDASAN:
            1. PENCATATAN (TRANSACTION): Pemasukan -> "INCOME". Pengeluaran -> "EXPENSE". LANGSUNG EKSEKUSI jika jelas. Tunda ke 'pending_transaction' HANYA jika sangat aneh.
            2. PERTANYAAN SALDO/UANG: Jika ditanya berapa uang/saldo saya, lihat data [SALDO UANG SAYA SAAT INI].
            3. FORMAT UANG: WAJIB gunakan titik sebagai pemisah ribuan pada teks 'ai_response' (Contoh: Rp 5.000.000).
            4. TANGGAL & LAPORAN (VIEW_REPORT): Cari di riwayat jika tanya tanggal. Jika minta rincian spesifik, set action_type "VIEW_REPORT", "ITEM_DETAILS", dan "CUSTOM_RANGE".
            5. UTANG/PIUTANG & PEMBAYARAN: 
               - Pinjam uang DARI orang -> "DEBT_RECORD", debt_type: "DEBT".
               - Minjamin uang KE orang -> "DEBT_RECORD", debt_type: "RECEIVABLE".
               - Bayar hutang / Terima pelunasan piutang -> action_type: "DEBT_PAYMENT". WAJIB masukkan ke dalam array 'transactions' dan isi field 'contact_name' dengan nama orangnya serta 'amount'.
            6. BUAT KATEGORI: Jika disuruh -> action_type: "CREATE_CATEGORY".
               
            PERINGATAN: 'ai_response' WAJIB bahasa natural. DILARANG MENGCOPY TEMPLATE JSON INI KE DALAM JAWABAN!
            
            FORMAT JSON WAJIB:
            {
              "action_type": "CHAT_ONLY" | "TRANSACTION" | "DEBT_RECORD" | "DEBT_PAYMENT" | "VIEW_REPORT" | "VIEW_CATEGORIES" | "CREATE_CATEGORY",
              "ai_response": "Tulis jawaban natural Anda di sini...",
              "pending_transaction": { "amount": 0, "type": "EXPENSE", "category_id": 1, "category_name": "Nama", "clean_note": "Catatan", "contact_name": "", "debt_type": "DEBT", "is_new_category": false, "transaction_date": "dd-MM-yyyy HH:mm" },
              "report_filter": { "report_type": "SUMMARY" | "ITEM_DETAILS" | "CATEGORY_BREAKDOWN", "time_range": "MONTHLY" | "CUSTOM_RANGE", "start_date": "", "end_date": "", "target_category": "", "target_keyword": "" },
              "new_category": { "name": "Nama Kategori", "type": "INCOME" | "EXPENSE", "parent_category_id": "" },
              "transactions": [{ "amount": 0, "type": "EXPENSE", "category_id": 1, "category_name": "Nama Kategori", "clean_note": "Catatan", "contact_name": "WAJIB DIISI JIKA BAYAR UTANG", "debt_type": "DEBT", "is_new_category": false, "transaction_date": "dd-MM-yyyy HH:mm" }]
            }
        """.trimIndent()
    }

    suspend fun sendMessageToAI(userMessage: String): String = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        
        val apiKey = prefs.getString("ai_api_key", prefs.getString("groq_key_override", "")) ?: ""
        val aiModel = prefs.getString("ai_model", "llama-3.3-70b-versatile") ?: "llama-3.3-70b-versatile"
        
        if (apiKey.isEmpty()) return@withContext "⚠️ Sistem dikunci! Silakan masukkan API Key AI di menu Pengaturan (Kecerdasan & Server) terlebih dahulu."

        val catContext = java.lang.StringBuilder()
        val myDebtContext = java.lang.StringBuilder()
        val otherReceivableContext = java.lang.StringBuilder()
        val txContext = java.lang.StringBuilder()
        var currentBalanceStr = "Rp 0"

        try {
            // 🔥 TAHAP 1: EKSTRAKSI DATA DARI FIRESTORE UNTUK "MEMBERI MAKAN" OTAK AI
            val allTxSnapshot = firestore.collection("transactions").get().await()
            var totalInc = 0.0
            var totalExp = 0.0
            val sortedTx = allTxSnapshot.documents.sortedByDescending { it.getLong("timestamp") ?: 0L }
            
            for (doc in sortedTx) {
                val amt = doc.getDouble("amount") ?: 0.0
                val type = doc.getString("type") ?: "EXPENSE"
                if (type == "INCOME" || type == "DEBT") totalInc += amt else totalExp += amt
            }
            val formatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
            currentBalanceStr = formatter.format(totalInc - totalExp)

            val sdfTx = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale("id", "ID"))
            for (doc in sortedTx.take(50)) {
                val amt = doc.getDouble("amount") ?: 0.0
                val type = doc.getString("type") ?: "EXPENSE"
                val catName = doc.getString("categoryName") ?: "Umum"
                val note = doc.getString("note") ?: "Transaksi"
                val ts = doc.getLong("timestamp") ?: System.currentTimeMillis()
                txContext.append("- [${sdfTx.format(Date(ts))}] $note | Kategori: $catName | Tipe: $type | Nominal: Rp$amt\n")
            }

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
        } catch (e: Exception) { e.printStackTrace() }

        val sdfToday = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale("id", "ID"))
        val todayString = sdfToday.format(Date())

        // 🔥 TAHAP 2: MERAKIT PROMPT RAKSASA
        var finalSystemPrompt = prefs.getString("expert_system_prompt", DEFAULT_PROMPT) ?: DEFAULT_PROMPT
        if (finalSystemPrompt.contains("{TODAY_DATE}")) {
            finalSystemPrompt = finalSystemPrompt.replace("{TODAY_DATE}", todayString)
                .replace("{CURRENT_BALANCE}", currentBalanceStr)
                .replace("{CAT_CONTEXT}", catContext.toString())
                .replace("{MY_DEBT_CONTEXT}", if (myDebtContext.isEmpty()) "Bersih" else myDebtContext.toString())
                .replace("{OTHER_RECEIVABLE_CONTEXT}", if (otherReceivableContext.isEmpty()) "Bersih" else otherReceivableContext.toString())
                .replace("{TX_CONTEXT}", if (txContext.isEmpty()) "Belum ada riwayat" else txContext.toString())
        }

        // 🔥 TAHAP 3: MESIN ROUTER MULTI-AI YANG MERESPON JSON
        try {
            val rawResponse = when {
                aiModel.startsWith("gpt-") -> callOpenAICompatible("https://api.openai.com/v1/chat/completions", aiModel, apiKey, finalSystemPrompt, userMessage)
                aiModel.startsWith("deepseek") -> callOpenAICompatible("https://api.deepseek.com/chat/completions", aiModel, apiKey, finalSystemPrompt, userMessage)
                aiModel.startsWith("gemini") -> callGemini(aiModel, apiKey, finalSystemPrompt, userMessage)
                aiModel.startsWith("claude") -> callAnthropic(aiModel, apiKey, finalSystemPrompt, userMessage)
                else -> callOpenAICompatible("https://api.groq.com/openai/v1/chat/completions", aiModel, apiKey, finalSystemPrompt, userMessage) // Groq Default
            }
            
            if (rawResponse.startsWith("⚠️")) return@withContext rawResponse 
            
            // 🔥 TAHAP 4: MENYERAHKAN RESPON MENTAH AI KE FINANCIAL ASSISTANT UNTUK DIEKSEKUSI
            return@withContext assistant.parseAndExecuteRawAiResponse(rawResponse)
            
        } catch (e: Exception) {
            return@withContext "⚠️ Gangguan Jaringan Lokal: ${e.localizedMessage ?: "Timeout"}"
        }
    }

    private fun callOpenAICompatible(endpoint: String, model: String, apiKey: String, systemPrompt: String, userMessage: String): String {
        val url = URI(endpoint).toURL()
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
            put("model", model)
            put("messages", messagesArray)
            put("temperature", 0.7)
            // 🔥 WAJIB: Memaksa server merespon dalam format JSON Strict
            put("response_format", JSONObject().apply { put("type", "json_object") })
        }

        conn.outputStream.use { os -> os.write(jsonBody.toString().toByteArray(Charsets.UTF_8)) }

        if (conn.responseCode == 200) {
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            return JSONObject(reader.readText()).getJSONArray("choices")
                .getJSONObject(0).getJSONObject("message").getString("content").trim()
        } else {
            val errorReader = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream))
            return "⚠️ Server Error (HTTP ${conn.responseCode}): ${errorReader.readText()}"
        }
    }

    private fun callGemini(model: String, apiKey: String, systemPrompt: String, userMessage: String): String {
        val url = URI("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        val jsonBody = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply { put(JSONObject().apply { put("text", systemPrompt) }) })
            })
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply { put(JSONObject().apply { put("text", userMessage) }) })
                })
            })
            put("generationConfig", JSONObject().apply {
                // 🔥 WAJIB: Memaksa Gemini merespon dengan format JSON
                put("responseMimeType", "application/json")
                put("temperature", 0.7)
            })
        }

        conn.outputStream.use { os -> os.write(jsonBody.toString().toByteArray(Charsets.UTF_8)) }

        if (conn.responseCode == 200) {
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            return JSONObject(reader.readText()).getJSONArray("candidates")
                .getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                .getJSONObject(0).getString("text").trim()
        } else {
            val errorReader = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream))
            return "⚠️ Gemini Error (HTTP ${conn.responseCode}): ${errorReader.readText()}"
        }
    }

    private fun callAnthropic(model: String, apiKey: String, systemPrompt: String, userMessage: String): String {
        val url = URI("https://api.anthropic.com/v1/messages").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("x-api-key", apiKey)
        conn.setRequestProperty("anthropic-version", "2023-06-01")
        conn.doOutput = true

        val messagesArray = JSONArray().apply {
            // Claude tidak punya param JSON strict, jadi kita suntikkan di user message agar dia mengerti
            put(JSONObject().apply { put("role", "user"); put("content", "$userMessage\n\n[RESPOND STRICTLY IN JSON FORMAT]") })
        }

        val jsonBody = JSONObject().apply {
            put("model", model)
            put("max_tokens", 4096)
            put("temperature", 0.7)
            put("system", systemPrompt)
            put("messages", messagesArray)
        }

        conn.outputStream.use { os -> os.write(jsonBody.toString().toByteArray(Charsets.UTF_8)) }

        if (conn.responseCode == 200) {
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            return JSONObject(reader.readText()).getJSONArray("content")
                .getJSONObject(0).getString("text").trim()
        } else {
            val errorReader = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream))
            return "⚠️ Claude Error (HTTP ${conn.responseCode}): ${errorReader.readText()}"
        }
    }
}
