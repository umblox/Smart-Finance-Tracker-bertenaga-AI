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
            return@withContext "{\"ai_response\":\"⚠️ API Key Groq aman tidak ditemukan dalam sistem build.\"}"
        }

        val db = AppDatabase.getDatabase(context)
        
        // Load Kategori
        val categories = db.categoryDao().getAllCategories().first()
        val catContext = java.lang.StringBuilder()
        categories.forEach { catContext.append("- ID: ${it.id}, Nama: ${it.name}, Tipe: ${it.type}\n") }

        // Load Hutang Aktif
        val debts = db.debtDao().getAllDebts().first()
        val debtContext = java.lang.StringBuilder()
        debts.filter { !it.isPaid }.forEach { 
            debtContext.append("- ID: ${it.id}, Kontak: ${it.contactName}, Sisa: Rp ${it.remainingAmount}, Tipe: ${it.type}\n")
        }
        val systemPrompt = """
            Anda adalah core engine AI finansial pintar. Respons wajib berbentuk JSON terstruktur kaku.
            
            KATEGORI DI DATABASE:
            $catContext
            
            DAFTAR PINJAMAN AKTIF DI DATABASE:
            $debtContext
            
            ⚠️ ATURAN EMAS BAHASA ALAMI INDONESIA (JANGAN TERBALIK):
            - "Saya meminjam uang KEPADA [Nama]" ATAU "Saya berhutang KEPADA [Nama]" = DEBT (Hutang Saya, Kewajiban Membayar).
            - "[Nama] meminjam uang KEPADA SAYA" ATAU "Saya meminjamkan uang KEPADA [Nama]" = RECEIVABLE (Piutang Saya, Hak Menagih).
            - "Dani membayar cicilan" = DEBT_PAYMENT. Cocokkan debt_id dari daftar aktif di atas.

            SKEMA FORMAT JSON YANG WAJIB DIHASILKAN:
            - Transaksi Biasa: {"action_type":"TRANSACTION", "amount":50000, "type":"EXPENSE", "category_id":15, "category_name":"Lain-lain", "clean_note":"BELI BARANG", "ai_response":"Kalimat balasan manusia"}
            - Catat Hutang Baru (Saya yang utang): {"action_type":"DEBT_RECORD", "amount":100000, "contact_name":"BUDI", "debt_type":"DEBT", "ai_response":"Hutang Anda kepada Budi sebesar Rp 100.000 berhasil dicatat."}
            - Catat Piutang Baru (Orang lain utang ke saya): {"action_type":"DEBT_RECORD", "amount":50000, "contact_name":"DANI", "debt_type":"RECEIVABLE", "ai_response":"Piutang baru tercatat. Dani meminjam Rp 50.000 kepada Anda."}
            - Cicilan: {"action_type":"DEBT_PAYMENT", "debt_id":1, "pay_amount":20000, "ai_response":"Pembayaran cicilan Rp 20.000 berhasil."}
        """.trimIndent()

        try {
            val url = URL("https://api.groq.com/openai/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true

            // AKTIFKAN JSON MODE RESMI DARI GROQ CLOUD API
            val jsonBody = JSONObject().apply {
                put("model", "llama-3.1-8b-instant")
                val messagesArray = org.json.JSONArray().apply {
                    put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                    put(JSONObject().apply { put("role", "user"); put("content", userMessage) })
                }
                put("messages", messagesArray)
                put("temperature", 0.0)
                
                // FORCE RESMI SERVER UNTUK MERESPONS JSON OBJECT
                put("response_format", JSONObject().apply { put("type", "json_object") })
            }

            conn.outputStream.use { os -> os.write(jsonBody.toString().toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val rawResponse = JSONObject(reader.readText()).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content").trim()
                
                // Langsung kirim JSON murni ke asisten lokal Android
                return@withContext assistant.parseAndExecuteRawAiResponse(rawResponse)
            } else {
                return@withContext "⚠️ Koneksi Groq terputus (HTTP ${conn.responseCode})"
            }
        } catch (e: Exception) {
            return@withContext "⚠️ Cloud Groq penuh. Silakan ulangi pesan Anda."
        }
    }
}
