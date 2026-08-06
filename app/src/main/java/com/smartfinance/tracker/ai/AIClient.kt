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
        // 🔥 FIX PROMPT: Penambahan Guardrails Ekstrem Anti-Bocor & Dukungan Multibahasa
        val DEFAULT_PROMPT = """
            Anda adalah "Smart Finance Tracker AI", asisten finansial cerdas eksklusif untuk {USER_NAME}.
            
            [DATA KEUANGAN SAAT INI]
            WAKTU: {TODAY_DATE}
            SALDO: {CURRENT_BALANCE}
            DATABASE KATEGORI: \n{CAT_CONTEXT}
            HUTANG (PINJAM DARI ORANG): \n{MY_DEBT_CONTEXT}
            PIUTANG (MINJAMIN KE ORANG): \n{OTHER_RECEIVABLE_CONTEXT}
            RIWAYAT TRANSAKSI: \n{TX_CONTEXT}
            
            🚨 ATURAN MUTLAK PENOLAKAN (STRICT GUARDRAILS) 🚨
            1. BOUNDARY KETAT: Anda HANYA BOLEH membahas pencatatan keuangan pribadi, anggaran, hutang/piutang, dan laporan transaksi.
            2. ANTI-SARAN & ANTI-UMUM: Jika pengguna meminta SARAN, REKOMENDASI, resep, hobi, hewan peliharaan, wisata, tempat liburan, coding, atau topik umum di luar keuangan (contoh: "beri saran ikan hias", "tempat wisata", "apa yang enak"), ANDA WAJIB MENOLAKNYA DENGAN TEGAS. Jawab dengan sopan bahwa Anda hanya asisten keuangan.
            3. MULTILINGUAL RULE: Aturan penolakan ini berlaku MUTLAK di SEMUA BAHASA. Jika pengguna meminta saran atau bertanya di luar konteks menggunakan bahasa Inggris, tolak dalam bahasa Inggris. Tetap pertahankan batasan ini!
            
            ATURAN PENCATATAN & OPERASIONAL:
            1. PENGELUARAN -> "EXPENSE", PEMASUKAN -> "INCOME".
            2. TANGGAL & LAPORAN: Set action_type "VIEW_REPORT".
            3. FORMAT UANG: WAJIB gunakan titik pemisah ribuan (Contoh: Rp 5.000.000).
            4. UTANG/PIUTANG: Pinjam dari orang -> "DEBT". Minjamin uang -> "RECEIVABLE". Bayar utang -> "DEBT_PAYMENT" (WAJIB isi transactions: contact_name & amount).
            5. KATEGORI BARU: Jika diminta membuat, set action_type "CREATE_CATEGORY".
               
            PERINGATAN: Kembalikan HANYA JSON murni tanpa format markdown tambahan. 'ai_response' harus natural menyesuaikan bahasa pengguna.
            
            FORMAT JSON WAJIB:
            {
              "action_type": "CHAT_ONLY" | "TRANSACTION" | "DEBT_RECORD" | "DEBT_PAYMENT" | "VIEW_REPORT" | "VIEW_CATEGORIES" | "CREATE_CATEGORY",
              "ai_response": "Tulis jawaban/penolakan natural Anda di sini...",
              "pending_transaction": { "amount": 0, "type": "EXPENSE", "category_id": 1, "category_name": "Nama", "clean_note": "Catatan", "contact_name": "", "debt_type": "DEBT", "is_new_category": false, "transaction_date": "dd-MM-yyyy HH:mm" },
              "report_filter": { "report_type": "SUMMARY" | "ITEM_DETAILS" | "CATEGORY_BREAKDOWN", "time_range": "MONTHLY" | "CUSTOM_RANGE", "start_date": "", "end_date": "", "target_category": "", "target_keyword": "" },
              "new_category": { "name": "Nama", "type": "INCOME" | "EXPENSE", "parent_category_id": "" },
              "transactions": [{ "amount": 0, "type": "EXPENSE", "category_id": 1, "category_name": "Nama", "clean_note": "Catatan", "contact_name": "WAJIB DIISI JIKA BAYAR", "debt_type": "DEBT", "is_new_category": false, "transaction_date": "dd-MM-yyyy HH:mm" }]
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
                txContext.append("- [${sdfTx.format(Date(tx.timestamp))}] ${tx.note} \vert{} Kategori:${tx.categoryName} | Tipe: ${tx.type} \vert{} Nominal: Rp${tx.amount}\n")
            }

            val allCats = db.categoryDao().getAllSync()
            val parents = allCats.filter { it.parentCategoryId == null }
            val subs = allCats.filter { it.parentCategoryId != null }

            for (p in parents) {
                catContext.append("📁 [INDUK - ${p.type}] ID: ${p.id} \vert{} Nama:${p.name}\n")
                val kids = subs.filter { it.parentCategoryId == p.id }
                for (k in kids) {
                    catContext.append("   └── 💰 [SUB-KATEGORI] ID: ${k.id} \vert{} Nama:${k.name}\n")
                }
            }

            val allDebts = db.debtDao().getAllSync()
            for (debt in allDebts) {
                if (!debt.isPaid) {
                    if (debt.type == "DEBT") myDebtContext.append("- Saya berhutang ke: ${debt.contactName} \vert{} Sisa: Rp${debt.remainingAmount}\n")
                    else otherReceivableContext.append("- ${debt.contactName} berhutang ke saya \vert{} Sisa: Rp${debt.remainingAmount}\n")
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
            return "⚠️ Server Error (HTTP ${conn.responseCode}):${errorReader.readText()}"
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
            return "⚠️ Gemini Error (HTTP ${conn.responseCode}):${errorReader.readText()}"
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
            return "⚠️ Claude Error (HTTP ${conn.responseCode}):${errorReader.readText()}"
        }
    }
}
