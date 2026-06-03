package com.smartfinance.tracker.ai

import android.content.Context
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class GeminiClient(private val context: Context, private val assistant: FinancialAssistant) {

    suspend fun sendMessageToAI(userMessage: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GROQ_API_KEY

        if (apiKey.isEmpty()) {
            return@withContext "⚠️ API Key Groq aman tidak ditemukan dalam sistem build."
        }

        val db = AppDatabase.getDatabase(context)
        val categories = db.categoryDao().getAllCategories().first()
        val catContext = java.lang.StringBuilder()
        categories.forEach { catContext.append("- ID: ${it.id}, Nama: ${it.name}, Tipe: ${it.type}\n") }

        val debts = db.debtDao().getAllDebts().first()
        val debtContext = java.lang.StringBuilder()
        debts.filter { !it.isPaid }.forEach { 
            debtContext.append("- ID: ${it.id}, Kontak: ${it.contactName}, Sisa: Rp ${it.remainingAmount}, Tipe: ${it.type}\n")
        }

        // WAJIB MENYERTAKAN KATA "JSON" DI DALAM PROMPT AGAR GROQ BERFUNGSI NORMAL
        val rawSystemPrompt = """
            Anda adalah core engine AI finansial akuntansi. Anda WAJIB merespons HANYA dengan objek JSON murni.

            ⚠️ ATURAN LOGIKA ARAH UANG (SUBJEK vs OBJEK):
            - "SAYA meminjam KE [Nama]" -> Hutang (DEBT).
            - "[Nama] meminjam KE SAYA" -> Piutang (RECEIVABLE).
            - "[Nama] membayar cicilan KE SAYA" -> Cicilan Piutang (DEBT_PAYMENT).
            - "SAYA membayar cicilan KE [Nama]" -> Cicilan Hutang (DEBT_PAYMENT).
            - KATA KUNCI:
                - "KEPADA", "KE": Menunjukkan penerima uang.
                - "DARI", "OLEH": Menunjukkan sumber uang.
            
            JIKA user bilang "Gajian", maka itu adalah INCOME, Category ID 1.
            JIKA user bilang "Beli", maka itu adalah EXPENSE, Category ID sesuai yang relevan.
            
            JSON FORMAT WAJIB:
            {'action_type':'...', 'amount':..., 'contact_name':'...', 'debt_type':'...', 'category_id':..., 'type':'...', 'clean_note':'...', 'ai_response':'...'}
        """.trimIndent()

        val finalSystemPrompt = rawSystemPrompt
            .replace("CONTEXT_CATEGORIES", catContext.toString())
            .replace("CONTEXT_DEBTS", debtContext.toString())
            .replace("'", "\"")

        try {
            val url = URL("https://api.groq.com/openai/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true

            val jsonBody = JSONObject().apply {
                put("model", "llama-3.1-8b-instant")
                val messagesArray = org.json.JSONArray().apply {
                    put(JSONObject().apply { put("role", "system"); put("content", finalSystemPrompt) })
                    put(JSONObject().apply { put("role", "user"); put("content", userMessage) })
                }
                put("messages", messagesArray)
                put("temperature", 0.1) // Rendah agar konsisten menghasilkan format JSON resmi
                put("response_format", JSONObject().apply { put("type", "json_object") })
            }

            conn.outputStream.use { os -> os.write(jsonBody.toString().toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val rawResponse = JSONObject(reader.readText()).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content").trim()
                
                return@withContext assistant.parseAndExecuteRawAiResponse(rawResponse)
            } else {
                return@withContext "⚠️ Hubungan ke Groq terputus (HTTP ${conn.responseCode})"
            }
        } catch (e: Exception) {
            return@withContext "⚠️ Gangguan Jaringan Lokal: ${e.localizedMessage ?: "Timeout"}"
        }
    }
}
