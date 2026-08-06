package com.smartfinance.tracker.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
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
import com.smartfinance.tracker.data.local.DatabaseProvider

class AIClient(private val context: Context, private val assistant: FinancialAssistant) {

    private val db = DatabaseProvider.db

    companion object {
        // 🔥 FIX PROMPT: Penegasan EKSTREM untuk action_type DEBT_RECORD agar AI tidak mencatatnya sebagai transaksi biasa!
        val DEFAULT_PROMPT = """
            Anda adalah Asisten Finansial cerdas untuk {USER_NAME} dalam aplikasi "Smart Finance Tracker".
            Dilarang menjawab pertanyaan selain tugasmu dalam aplikasi yang berkaitan dengan financial tracker!
            WAKTU SAAT INI: {TODAY_DATE}
            
            [SALDO UANG SAYA SAAT INI]: {CURRENT_BALANCE}
            [DATABASE KATEGORI]: \n{CAT_CONTEXT}
            [HUTANG SAYA (SAYA PINJAM)]: \n{MY_DEBT_CONTEXT}
            [PIUTANG SAYA (ORANG PINJAM)]: \n{OTHER_RECEIVABLE_CONTEXT}
            [RIWAYAT TRANSAKSI TERAKHIR]: \n{TX_CONTEXT}
            
            ATURAN MUTLAK KECERDASAN:
            1. PENCATATAN TRANSAKSI BIASA: action_type -> "TRANSACTION". Pemasukan -> "INCOME". Pengeluaran -> "EXPENSE".
            2. PERTANYAAN SALDO/UANG: Jika ditanya berapa uang/saldo saya, lihat data [SALDO UANG SAYA SAAT INI]. action_type -> "CHAT_ONLY".
            3. FORMAT UANG: WAJIB gunakan titik sebagai pemisah ribuan pada teks 'ai_response' (Contoh: Rp 5.000.000).
            4. TANGGAL & LAPORAN: Cari di riwayat jika tanya tanggal. Jika minta rincian spesifik, set action_type "VIEW_REPORT".
            5. TRANSAKSI UTANG / PIUTANG (JIKA ADA KATA PINJAM / HUTANG):
               - WAJIB SET action_type: "DEBT_RECORD" (DILARANG KERAS MENGGUNAKAN "TRANSACTION"!).
               - JIKA SAYA MEMINJAM UANG DARI ORANG: debt_type: "DEBT".
               - JIKA ORANG LAIN MEMINJAM UANG DARI SAYA: debt_type: "RECEIVABLE" (Contoh: "Afnan pinjam uang", "Bayu meminjam 40000"). JANGAN TERBALIK!
               - PEMBAYARAN / PELUNASAN -> action_type: "DEBT_PAYMENT".
               - WAJIB masukkan ke dalam array 'transactions' dan isi 'contact_name'.
            6. KATEGORI (LIHAT & BUAT): 
               - Jika diminta melihat kategori spesifik (contoh: sub kategori belanja, pemasukan), gunakan action_type: "CHAT_ONLY". Tampilkan HANYA namanya secara rapi menggunakan emoji (📁 untuk Induk, └── 💰 untuk Sub). JANGAN tampilkan kata ID, angka ID, atau teks "[INDUK/SUB]".
               - Gunakan action_type: "VIEW_CATEGORIES" HANYA jika diminta melihat SEMUA kategori sekaligus.
               - Jika membuat baru -> action_type: "CREATE_CATEGORY". Tipe WAJIB "INCOME" atau "EXPENSE".
            7. PENOLAKAN KETAT: TOLAK permintaan saran (wisata, resep, hobi, dll) dan pertanyaan umum di luar keuangan. Aturan ini BERLAKU MUTLAK di SEMUA BAHASA.
               
            PERINGATAN: 'ai_response' WAJIB bahasa natural. DILARANG MENGCOPY TEMPLATE JSON INI KE DALAM JAWABAN!
            
            FORMAT JSON WAJIB:
            {
              "action_type": "CHAT_ONLY" | "TRANSACTION" | "DEBT_RECORD" | "DEBT_PAYMENT" | "VIEW_REPORT" | "VIEW_CATEGORIES" | "CREATE_CATEGORY",
              "ai_response": "Tulis jawaban natural Anda di sini...",
              "pending_transaction": { "amount": 0, "type": "EXPENSE", "category_id": 1, "category_name": "Nama", "clean_note": "Catatan", "contact_name": "", "debt_type": "DEBT", "is_new_category": false, "transaction_date": "dd-MM-yyyy HH:mm" },
              "report_filter": { "report_type": "SUMMARY" | "ITEM_DETAILS" | "CATEGORY_BREAKDOWN" | "TOP_EXPENSE", "time_range": "TODAY" | "WEEKLY" | "MONTHLY" | "LAST_MONTH" | "YEARLY" | "CUSTOM_RANGE", "start_date": "dd-MM-yyyy", "end_date": "dd-MM-yyyy", "target_category": "", "target_keyword": "" },
              "new_category": { "name": "Nama Kategori", "type": "INCOME" | "EXPENSE", "parent_category_id": "" },
              "transactions": [{ "amount": 0, "type": "EXPENSE", "category_id": 1, "category_name": "Nama Kategori", "clean_note": "Catatan", "contact_name": "WAJIB DIISI JIKA CATAT/BAYAR UTANG", "debt_type": "DEBT", "is_new_category": false, "transaction_date": "dd-MM-yyyy HH:mm" }]
            }
        """.trimIndent()
    }

    suspend fun sendMessageToAI(userMessage: String): String = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        
        val apiKey = prefs.getString("ai_api_key", prefs.getString("groq_key_override", "")) ?: ""
        val aiModel = prefs.getString("ai_model", "llama-3.3-70b-versatile") ?: "llama-3.3-70b-versatile"
        
        val savedName = prefs.getString("user_name", "")?.trim()
        val userName = if (savedName.isNullOrEmpty()) "Pengguna" else savedName
        
        if (apiKey.isEmpty()) return@withContext "⚠️ Sistem dikunci! Silakan masukkan API Key AI di menu Pengaturan terlebih dahulu."

        val catContext = java.lang.StringBuilder()
        val myDebtContext = java.lang.StringBuilder()
        val otherReceivableContext = java.lang.StringBuilder()
        val txContext = java.lang.StringBuilder()
        var currentBalanceStr = "Rp 0"

        try {
            val allTx = db.transactionDao().getAllSync()
            var totalInc = 0.0
            var totalExp = 0.0
            
            for (tx in allTx) {
                if (tx.type == "INCOME" || tx.type == "DEBT") totalInc += tx.amount else totalExp += tx.amount
            }
            val formatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
            currentBalanceStr = formatter.format(totalInc - totalExp)

            val sdfTx = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale("id", "ID"))
            for (tx in allTx.take(50)) {
                txContext.append("- [${sdfTx.format(Date(tx.timestamp))}] ${tx.note} | Kategori: ${tx.categoryName} | Tipe: ${tx.type} | Nominal: Rp${tx.amount}\n")
            }

            val allCats = db.categoryDao().getAllSync()
            val parents = allCats.filter { it.parentCategoryId == null }
            val subs = allCats.filter { it.parentCategoryId != null }

            for (p in parents) {
                catContext.append("📁 [INDUK - ${p.type}] ID: ${p.id} | Nama: ${p.name}\n")
                val kids = subs.filter { it.parentCategoryId == p.id }
                for (k in kids) {
                    catContext.append("   └── 💰 [SUB-KATEGORI] ID: ${k.id} | Nama: ${k.name}\n")
                }
            }

            val allDebts = db.debtDao().getAllSync()
            for (debt in allDebts) {
                if (!debt.isPaid) {
                    if (debt.type == "DEBT") myDebtContext.append("- Saya berhutang ke: ${debt.contactName} | Sisa: Rp ${debt.remainingAmount}\n")
                    else otherReceivableContext.append("- ${debt.contactName} berhutang ke saya | Sisa: Rp ${debt.remainingAmount}\n")
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        val sdfToday = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale("id", "ID"))
        val todayString = sdfToday.format(Date())

        var finalSystemPrompt = prefs.getString("expert_system_prompt", DEFAULT_PROMPT) ?: DEFAULT_PROMPT
        
        finalSystemPrompt = finalSystemPrompt.replace("{USER_NAME}", userName)
        
        if (finalSystemPrompt.contains("{TODAY_DATE}")) {
            finalSystemPrompt = finalSystemPrompt.replace("{TODAY_DATE}", todayString)
                .replace("{CURRENT_BALANCE}", currentBalanceStr)
                .replace("{CAT_CONTEXT}", catContext.toString())
                .replace("{MY_DEBT_CONTEXT}", if (myDebtContext.isEmpty()) "Bersih" else myDebtContext.toString())
                .replace("{OTHER_RECEIVABLE_CONTEXT}", if (otherReceivableContext.isEmpty()) "Bersih" else otherReceivableContext.toString())
                .replace("{TX_CONTEXT}", if (txContext.isEmpty()) "Belum ada riwayat" else txContext.toString())
        }

        try {
            val rawResponse = when {
                aiModel.startsWith("gpt-") -> callOpenAICompatible("https://api.openai.com/v1/chat/completions", aiModel, apiKey, finalSystemPrompt, userMessage)
                aiModel.startsWith("deepseek") -> callOpenAICompatible("https://api.deepseek.com/chat/completions", aiModel, apiKey, finalSystemPrompt, userMessage)
                aiModel.startsWith("gemini") -> callGemini(aiModel, apiKey, finalSystemPrompt, userMessage)
                aiModel.startsWith("claude") -> callAnthropic(aiModel, apiKey, finalSystemPrompt, userMessage)
                else -> callOpenAICompatible("https://api.groq.com/openai/v1/chat/completions", aiModel, apiKey, finalSystemPrompt, userMessage)
            }
            
            if (rawResponse.startsWith("⚠️")) return@withContext rawResponse 
            
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
